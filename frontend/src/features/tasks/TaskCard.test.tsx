import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Task } from "@/lib/api/types";
import { TaskCard } from "./TaskCard";

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: "task-1",
    templateId: "tpl-1",
    templateTitle: "柔光宠物肖像",
    status: "SUCCEEDED",
    currentStage: "FINALIZE",
    steps: [
      { stage: "ASSET_CHECK", status: "SUCCEEDED", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
      { stage: "GENERATE", status: "SUCCEEDED", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
      { stage: "FINALIZE", status: "SUCCEEDED", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
    ],
    result: {
      previewUrl: "/api/v1/tasks/task-1/result",
      mimeType: "image/png",
      width: 1200,
      height: 900,
    },
    error: null,
    createdAt: "2026-11-13T03:00:00.000Z",
    updatedAt: "2026-11-13T03:00:04.000Z",
    startedAt: "2026-11-13T03:00:01.000Z",
    finishedAt: "2026-11-13T03:00:04.000Z",
    revision: 4,
    ...overrides,
  };
}

describe("TaskCard", () => {
  it("renders a failed task with its failed stage, readable reason and skipped later stages", () => {
    render(
      <TaskCard
        task={makeTask({
          status: "FAILED",
          currentStage: "GENERATE",
          result: null,
          error: { code: "SIMULATED_FAILURE", message: "模拟失败：模拟生成阶段", failedStage: "GENERATE" },
          steps: [
            { stage: "ASSET_CHECK", status: "SUCCEEDED", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
            { stage: "GENERATE", status: "FAILED", startedAt: null, finishedAt: null, errorCode: "SIMULATED_FAILURE", errorMessage: "模拟失败" },
            { stage: "FINALIZE", status: "SKIPPED", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
          ],
        })}
      />,
    );

    expect(screen.getByText("制作失败")).toBeInTheDocument();
    expect(screen.getByText("模拟生成阶段")).toBeInTheDocument();
    expect(screen.getByText("模拟失败：模拟生成阶段")).toBeInTheDocument();
    expect(screen.getByText("已跳过")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("keeps the task successful but reports an unavailable preview when the image fails to load", () => {
    render(<TaskCard task={makeTask()} />);
    const image = screen.getByRole("img");
    fireEvent.error(image);
    expect(screen.getByText(/结果预览不可用/)).toBeInTheDocument();
    expect(screen.getByText("已完成")).toBeInTheDocument();
  });

  it("shows a per-task query error with a retry action without changing the task status", () => {
    const onRetry = vi.fn();
    render(
      <TaskCard
        task={makeTask({ status: "RUNNING", result: null, currentStage: "GENERATE" })}
        queryError={{ message: "状态暂时无法更新", retryable: true }}
        onRetry={onRetry}
      />,
    );

    expect(screen.getByText("状态暂时无法更新")).toBeInTheDocument();
    expect(screen.getByText("制作中")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "恢复查询" }));
    expect(onRetry).toHaveBeenCalledWith("task-1");
  });

  it("does not offer retry when the task is not accessible", () => {
    render(
      <TaskCard
        task={makeTask({ status: "RUNNING", result: null })}
        queryError={{ message: "该任务不可访问", retryable: false }}
        onRetry={vi.fn()}
      />,
    );

    expect(screen.getByText("该任务不可访问")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "恢复查询" })).not.toBeInTheDocument();
  });
});
