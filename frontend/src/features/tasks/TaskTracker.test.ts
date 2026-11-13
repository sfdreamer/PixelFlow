import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api/client";
import type { Task } from "@/lib/api/types";
import { TaskTracker } from "./TaskTracker";

function makeTask(id: string, overrides: Partial<Task> = {}): Task {
  return {
    id,
    templateId: "tpl-1",
    templateTitle: "柔光宠物肖像",
    status: "RUNNING",
    currentStage: "GENERATE",
    steps: [
      { stage: "ASSET_CHECK", status: "SUCCEEDED", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
      { stage: "GENERATE", status: "RUNNING", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
      { stage: "FINALIZE", status: "PENDING", startedAt: null, finishedAt: null, errorCode: null, errorMessage: null },
    ],
    result: null,
    error: null,
    createdAt: "2026-11-13T02:00:00.000Z",
    updatedAt: "2026-11-13T02:00:01.000Z",
    startedAt: "2026-11-13T02:00:00.500Z",
    finishedAt: null,
    revision: 1,
    ...overrides,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("TaskTracker", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("polls non-terminal tasks on a fixed interval and stops once all are terminal", async () => {
    const fetchTask = vi
      .fn()
      .mockResolvedValueOnce(makeTask("t1", { revision: 2 }))
      .mockResolvedValueOnce(makeTask("t1", { status: "SUCCEEDED", currentStage: "FINALIZE", revision: 3 }));
    const tracker = new TaskTracker({ fetchTask, intervalMs: 2000 });
    tracker.sync([makeTask("t1")]);

    expect(fetchTask).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(2000);
    expect(fetchTask).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(2000);
    expect(fetchTask).toHaveBeenCalledTimes(2);
    expect(tracker.getSnapshot().tasks[0].status).toBe("SUCCEEDED");

    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetchTask).toHaveBeenCalledTimes(2);
    expect(tracker.getSnapshot().polling).toBe(false);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("ignores lower revisions and never lets a non-terminal snapshot replace a terminal one", () => {
    const fetchTask = vi.fn();
    const tracker = new TaskTracker({ fetchTask });

    tracker.sync([makeTask("t1", { revision: 4 })]);
    tracker.sync([makeTask("t1", { revision: 2, currentStage: "ASSET_CHECK" })]);
    expect(tracker.getSnapshot().tasks[0].revision).toBe(4);

    tracker.sync([makeTask("t1", { status: "SUCCEEDED", revision: 5 })]);
    tracker.sync([makeTask("t1", { status: "RUNNING", revision: 9 })]);
    expect(tracker.getSnapshot().tasks[0].status).toBe("SUCCEEDED");
  });

  it("keeps the last good task on a query failure and pauses only that task", async () => {
    let failA = true;
    const fetchTask = vi.fn(async (id: string) => {
      if (id === "a") {
        if (failA) {
          throw new ApiError(503, "DEPENDENCY_UNAVAILABLE", "服务暂时不可用");
        }
        return makeTask("a", { revision: 5 });
      }
      return makeTask("b", { revision: 2 });
    });
    const tracker = new TaskTracker({ fetchTask });

    tracker.sync([makeTask("a"), makeTask("b", { createdAt: "2026-11-13T01:00:00.000Z" })]);
    await vi.advanceTimersByTimeAsync(2000);

    const snapshot = tracker.getSnapshot();
    expect(snapshot.tasks.find((task) => task.id === "a")?.status).toBe("RUNNING");
    expect(snapshot.errors.a).toMatchObject({ message: "状态暂时无法更新", retryable: true });
    expect(snapshot.paused).toContain("a");
    expect(snapshot.errors.b).toBeUndefined();

    const callsBefore = fetchTask.mock.calls.length;
    await vi.advanceTimersByTimeAsync(4000);
    const laterCalls = fetchTask.mock.calls.slice(callsBefore);
    expect(laterCalls.length).toBeGreaterThan(0);
    expect(laterCalls.every(([id]) => id === "b")).toBe(true);

    failA = false;
    tracker.retry("a");
    await vi.advanceTimersByTimeAsync(2000);
    expect(tracker.getSnapshot().paused).not.toContain("a");
    expect(tracker.getSnapshot().tasks.find((task) => task.id === "a")?.revision).toBe(5);
  });

  it("stops everything on 401 and reports the expired session", async () => {
    const onUnauthorized = vi.fn();
    const fetchTask = vi.fn().mockRejectedValue(new ApiError(401, "SESSION_REQUIRED", "会话已失效"));
    const tracker = new TaskTracker({ fetchTask, onUnauthorized });

    tracker.sync([makeTask("a")]);
    await vi.advanceTimersByTimeAsync(2000);

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(tracker.getSnapshot().polling).toBe(false);
    await vi.advanceTimersByTimeAsync(6000);
    expect(fetchTask).toHaveBeenCalledTimes(1);
  });

  it("stops only the task that returns 404 and offers no retry", async () => {
    const fetchTask = vi.fn(async (id: string) => {
      if (id === "gone") {
        throw new ApiError(404, "TASK_NOT_FOUND", "任务不存在");
      }
      return makeTask(id, { revision: 2 });
    });
    const tracker = new TaskTracker({ fetchTask });
    tracker.sync([makeTask("gone"), makeTask("alive", { createdAt: "2026-11-13T01:00:00.000Z" })]);

    await vi.advanceTimersByTimeAsync(2000);
    const snapshot = tracker.getSnapshot();
    expect(snapshot.errors.gone).toMatchObject({ message: "该任务不可访问", retryable: false });
    expect(snapshot.tasks.find((task) => task.id === "alive")?.revision).toBe(2);

    tracker.retry("gone");
    expect(tracker.getSnapshot().paused).toContain("gone");
  });

  it("limits each round to four in-flight requests and rotates through the rest", async () => {
    const pending: Array<ReturnType<typeof deferred<Task>>> = [];
    const requested: string[] = [];
    const fetchTask = vi.fn((taskId: string) => {
      requested.push(taskId);
      const item = deferred<Task>();
      pending.push(item);
      return item.promise;
    });
    const tracker = new TaskTracker({ fetchTask, maxConcurrent: 4 });
    tracker.sync(["a", "b", "c", "d", "e", "f"].map((id) => makeTask(id)));

    await vi.advanceTimersByTimeAsync(2000);
    expect(fetchTask).toHaveBeenCalledTimes(4);
    pending.forEach((item, index) => item.resolve(makeTask(requested[index], { revision: 2 })));
    await vi.advanceTimersByTimeAsync(2000);

    expect(requested.length).toBeGreaterThanOrEqual(6);
    expect(new Set(requested).size).toBe(6);
  });

  it("ignores responses that arrive after stop()", async () => {
    const pending = deferred<Task>();
    const fetchTask = vi.fn(() => pending.promise);
    const tracker = new TaskTracker({ fetchTask });
    tracker.sync([makeTask("a")]);
    await vi.advanceTimersByTimeAsync(2000);

    tracker.stop();
    pending.resolve(makeTask("a", { status: "SUCCEEDED", revision: 7 }));
    await Promise.resolve();
    await Promise.resolve();

    expect(tracker.getSnapshot().tasks[0].status).toBe("RUNNING");
    expect(vi.getTimerCount()).toBe(0);
  });
});
