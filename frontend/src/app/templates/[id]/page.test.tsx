import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Session, Task, Template } from "@/lib/api/types";
import { IdentityProvider } from "@/features/identity/IdentityProvider";
import TemplateDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "tpl-1" }),
}));

vi.mock("@/lib/api/endpoints", () => ({
  api: {
    ensureSession: vi.fn(),
    getTemplate: vi.fn(),
    listTasks: vi.fn(),
    createTask: vi.fn(),
    uploadAsset: vi.fn(),
    getTask: vi.fn(),
  },
}));

const mocked = vi.mocked(api);

const session: Session = {
  userId: "user-1",
  displayName: "游客-AAAA",
  expiresAt: "2026-12-13T00:00:00.000Z",
};

const template: Template = {
  id: "tpl-1",
  categoryId: "cat-1",
  title: "柔光宠物肖像",
  description: "上传一张宠物正面照。",
  coverUrl: "/api/v1/templates/tpl-1/cover",
  requiredImageCount: 1,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024,
};

const queuedTask: Task = {
  id: "task-1",
  templateId: "tpl-1",
  templateTitle: "柔光宠物肖像",
  status: "QUEUED",
  currentStage: null,
  steps: [
    { stage: "ASSET_CHECK", status: "PENDING", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
    { stage: "GENERATE", status: "PENDING", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
    { stage: "FINALIZE", status: "PENDING", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
  ],
  result: null,
  error: null,
  createdAt: "2026-11-13T03:00:00.000Z",
  updatedAt: "2026-11-13T03:00:00.000Z",
  startedAt: null,
  finishedAt: null,
  revision: 0,
};

function renderPage() {
  return render(
    <IdentityProvider>
      <TemplateDetailPage />
    </IdentityProvider>,
  );
}

async function uploadOneImage(container: HTMLElement) {
  const input = container.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) {
    throw new Error("file input not found");
  }
  const file = new File([new Uint8Array(64)], "pet.png", { type: "image/png" });
  await act(async () => {
    fireEvent.change(input, { target: { files: [file] } });
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe("TemplateDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocked.ensureSession.mockResolvedValue(session);
    mocked.getTemplate.mockResolvedValue(template);
    mocked.listTasks.mockResolvedValue({ items: [], nextCursor: null });
    mocked.getTask.mockResolvedValue(queuedTask);
    mocked.uploadAsset.mockResolvedValue({
      id: "asset-1",
      templateId: "tpl-1",
      originalName: "pet.png",
      mimeType: "image/png",
      sizeBytes: 64,
      width: 1200,
      height: 900,
      previewUrl: "/api/v1/assets/asset-1/content",
      createdAt: "2026-11-13T03:00:00.000Z",
    });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      writable: true,
      value: vi.fn(() => "blob:preview"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      writable: true,
      value: vi.fn(),
    });
  });

  it("shows the template requirements and keeps submit disabled until every slot is ready", async () => {
    const { container } = renderPage();

    expect(await screen.findByRole("heading", { name: "柔光宠物肖像" })).toBeInTheDocument();
    expect(screen.getByText(/需要 1 张图片/)).toBeInTheDocument();

    const submit = screen.getByRole("button", { name: "开始制作" });
    expect(submit).toBeDisabled();

    await uploadOneImage(container);
    await waitFor(() => expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled());
  });

  it("ends the submitting state when the task is accepted, without waiting for completion", async () => {
    mocked.createTask.mockResolvedValue(queuedTask);
    const { container } = renderPage();
    await screen.findByRole("heading", { name: "柔光宠物肖像" });
    await uploadOneImage(container);
    await waitFor(() => expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled());

    fireEvent.click(screen.getByRole("button", { name: "开始制作" }));

    expect(await screen.findByText(/任务已受理/)).toBeInTheDocument();
    expect(mocked.createTask).toHaveBeenCalledTimes(1);
    expect(mocked.createTask).toHaveBeenCalledWith(
      { templateId: "tpl-1", assetIds: ["asset-1"] },
      expect.anything(),
    );
    expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled();
    expect(await screen.findByText("排队中")).toBeInTheDocument();
  });

  it("marks an unconfirmed acceptance as uncertain and never resubmits automatically", async () => {
    mocked.createTask.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "网络请求失败，请检查连接后重试"));
    const { container } = renderPage();
    await screen.findByRole("heading", { name: "柔光宠物肖像" });
    await uploadOneImage(container);
    await waitFor(() => expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled());

    fireEvent.click(screen.getByRole("button", { name: "开始制作" }));

    expect(await screen.findByText("提交结果暂未确认，请刷新历史确认")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看历史确认" })).toBeInTheDocument();
    expect(mocked.createTask).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled();
  });

  it("shows a readable state when the template does not exist", async () => {
    mocked.getTemplate.mockRejectedValue(new ApiError(404, "TEMPLATE_NOT_FOUND", "模板不存在或已下架"));
    renderPage();
    expect(await screen.findByRole("heading", { name: "模板不存在或已下架" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "返回模板库" })).toBeInTheDocument();
  });
});
