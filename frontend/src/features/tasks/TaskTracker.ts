import { ApiError } from "@/lib/api/client";
import type { Task } from "@/lib/api/types";

export interface TaskQueryError {
  message: string;
  retryable: boolean;
  code?: string;
}

export interface TaskTrackerSnapshot {
  tasks: Task[];
  errors: Record<string, TaskQueryError>;
  paused: string[];
  polling: boolean;
}

export interface TaskTrackerOptions {
  fetchTask: (taskId: string, signal: AbortSignal) => Promise<Task>;
  intervalMs?: number;
  maxConcurrent?: number;
  onUnauthorized?: () => void;
  setTimeoutFn?: (callback: () => void, ms: number) => ReturnType<typeof setTimeout>;
  clearTimeoutFn?: (handle: ReturnType<typeof setTimeout>) => void;
}

const TERMINAL = new Set(["SUCCEEDED", "FAILED"]);

/**
 * Page-level polling manager.
 *
 * - Recursive setTimeout (never overlapping intervals), at most `maxConcurrent` requests per round.
 * - Only non-terminal, non-paused tasks are polled; an empty set stops the loop entirely.
 * - Snapshots are applied only while the captured generation is still current, and never with a
 *   lower revision than the local copy; terminal states are never overwritten.
 * - A query failure keeps the last good Task and pauses auto-tracking for that task only. A 401
 *   stops everything; a 404 stops just that task.
 */
export class TaskTracker {
  private readonly options: Required<Pick<TaskTrackerOptions, "intervalMs" | "maxConcurrent">> & TaskTrackerOptions;
  private readonly tasks = new Map<string, Task>();
  private readonly errors = new Map<string, TaskQueryError>();
  private readonly paused = new Set<string>();
  private readonly listeners = new Set<() => void>();
  private snapshot: TaskTrackerSnapshot = { tasks: [], errors: {}, paused: [], polling: false };
  private timer: ReturnType<typeof setTimeout> | null = null;
  private generation = 0;
  private abortController = new AbortController();
  private cursor = 0;
  private polling = false;

  constructor(options: TaskTrackerOptions) {
    this.options = { intervalMs: 2000, maxConcurrent: 4, ...options };
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): TaskTrackerSnapshot => this.snapshot;

  /** Adds or refreshes a batch (history load, task creation). Resumes polling if needed. */
  sync(tasks: Task[]): void {
    for (const task of tasks) {
      this.apply(task);
    }
    this.emit();
    this.ensureScheduled();
  }

  upsert(task: Task): void {
    this.apply(task);
    this.emit();
    this.ensureScheduled();
  }

  retry(taskId: string): void {
    const error = this.errors.get(taskId);
    if (error && !error.retryable) {
      return;
    }
    this.errors.delete(taskId);
    this.paused.delete(taskId);
    this.emit();
    this.ensureScheduled();
  }

  /** Cancels timers and in-flight requests. The tracker can be reused by a later sync(). */
  stop(): void {
    this.generation += 1;
    if (this.timer !== null) {
      (this.options.clearTimeoutFn ?? clearTimeout)(this.timer);
      this.timer = null;
    }
    this.abortController.abort();
    this.abortController = new AbortController();
    this.polling = false;
    this.emit();
  }

  dispose(): void {
    this.stop();
    this.listeners.clear();
  }

  private apply(task: Task): void {
    const current = this.tasks.get(task.id);
    if (current) {
      if (TERMINAL.has(current.status)) {
        return;
      }
      if (task.revision < current.revision) {
        return;
      }
    }
    this.tasks.set(task.id, task);
    if (TERMINAL.has(task.status)) {
      this.paused.delete(task.id);
      this.errors.delete(task.id);
    }
  }

  private activeIds(): string[] {
    const ids: string[] = [];
    for (const task of this.tasks.values()) {
      if (!TERMINAL.has(task.status) && !this.paused.has(task.id)) {
        ids.push(task.id);
      }
    }
    return ids.sort();
  }

  private ensureScheduled(): void {
    if (this.timer !== null) {
      return;
    }
    if (this.activeIds().length === 0) {
      if (this.polling) {
        this.polling = false;
        this.emit();
      }
      return;
    }
    this.schedule();
  }

  private schedule(): void {
    const generation = this.generation;
    const setTimeoutFn = this.options.setTimeoutFn ?? setTimeout;
    this.timer = setTimeoutFn(() => {
      void this.tick(generation);
    }, this.options.intervalMs);
  }

  private async tick(generation: number): Promise<void> {
    this.timer = null;
    if (generation !== this.generation) {
      return;
    }
    const ids = this.activeIds();
    if (ids.length === 0) {
      this.polling = false;
      this.emit();
      return;
    }
    this.polling = true;
    this.emit();

    const batch = this.rotate(ids, this.options.maxConcurrent);
    const signal = this.abortController.signal;
    await Promise.allSettled(batch.map((id) => this.pollOne(id, generation, signal)));

    if (generation !== this.generation) {
      return;
    }
    if (this.activeIds().length > 0) {
      this.schedule();
    } else {
      this.polling = false;
      this.emit();
    }
  }

  /** Rotates the window so more than maxConcurrent tasks still get polled over time. */
  private rotate(ids: string[], max: number): string[] {
    const size = Math.max(1, Math.min(max, ids.length));
    const batch: string[] = [];
    for (let i = 0; i < size; i += 1) {
      batch.push(ids[(this.cursor + i) % ids.length]);
    }
    this.cursor = (this.cursor + size) % ids.length;
    return batch;
  }

  private async pollOne(taskId: string, generation: number, signal: AbortSignal): Promise<void> {
    try {
      const task = await this.options.fetchTask(taskId, signal);
      if (generation !== this.generation) {
        return;
      }
      this.apply(task);
      this.errors.delete(taskId);
      this.paused.delete(taskId);
    } catch (error) {
      if (generation !== this.generation) {
        return;
      }
      if (signal.aborted) {
        return;
      }
      if (error instanceof ApiError && error.status === 401) {
        this.stop();
        this.options.onUnauthorized?.();
        return;
      }
      this.paused.add(taskId);
      if (error instanceof ApiError && error.status === 404) {
        this.errors.set(taskId, { message: "该任务不可访问", retryable: false, code: error.code });
      } else {
        this.errors.set(taskId, {
          message: "状态暂时无法更新",
          retryable: true,
          code: error instanceof ApiError ? error.code : "NETWORK_ERROR",
        });
      }
    } finally {
      if (generation === this.generation) {
        this.emit();
      }
    }
  }

  private emit(): void {
    this.snapshot = {
      tasks: [...this.tasks.values()].sort((a, b) => {
        const delta = Date.parse(b.createdAt) - Date.parse(a.createdAt);
        return delta !== 0 ? delta : b.id.localeCompare(a.id);
      }),
      errors: Object.fromEntries(this.errors),
      paused: [...this.paused],
      polling: this.polling,
    };
    for (const listener of this.listeners) {
      listener();
    }
  }
}
