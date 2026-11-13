import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Session } from "@/lib/api/types";
import { IdentityProvider } from "@/features/identity/IdentityProvider";
import { useTaskFeed } from "./useTaskFeed";

vi.mock("@/lib/api/endpoints", () => ({
  api: {
    ensureSession: vi.fn(),
    listTasks: vi.fn(),
    getTask: vi.fn(),
  },
}));

const mocked = vi.mocked(api);

const session: Session = {
  userId: "user-1",
  displayName: "游客-AAAA",
  expiresAt: "2026-12-13T00:00:00.000Z",
};

function Harness() {
  const feed = useTaskFeed();
  return (
    <div>
      <p data-testid="error">{feed.error ?? "none"}</p>
      <p data-testid="tasks">{feed.tasks.length}</p>
    </div>
  );
}

function renderFeed() {
  return render(
    <IdentityProvider>
      <Harness />
    </IdentityProvider>,
  );
}

describe("useTaskFeed", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocked.ensureSession.mockResolvedValue(session);
  });

  it("loads history once the anonymous session is ready", async () => {
    mocked.listTasks.mockResolvedValue({ items: [], nextCursor: null });
    renderFeed();

    await waitFor(() => expect(mocked.listTasks).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId("error")).toHaveTextContent("none"));
  });

  it("stops after a 401 instead of reloading in a loop", async () => {
    mocked.listTasks.mockRejectedValue(new ApiError(401, "SESSION_REQUIRED", "会话已失效"));
    renderFeed();

    await waitFor(() => expect(screen.getByTestId("error")).toHaveTextContent("会话已失效"));
    // 401 会把身份置为 expired；此处的关键断言是加载不会因为 epoch 变化而反复重发。
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(mocked.listTasks).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("tasks")).toHaveTextContent("0");
  });
});
