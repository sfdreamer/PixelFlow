"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";

import { errorMessage } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Task } from "@/lib/api/types";
import { useIdentity } from "@/features/identity/IdentityProvider";
import { TaskTracker, type TaskQueryError } from "./TaskTracker";

const PAGE_SIZE = 10;

export interface TaskFeed {
  tasks: Task[];
  errors: Record<string, TaskQueryError>;
  paused: string[];
  polling: boolean;
  loading: boolean;
  error: string | null;
  hasMore: boolean;
  reload: () => void;
  loadMore: () => void;
  tracker: TaskTracker;
}

/**
 * Loads a page of history and keeps only this page's non-terminal tasks under polling. The tracker
 * is rebuilt when the anonymous identity changes, so a previous user's responses can never land in
 * the new user's view.
 */
export function useTaskFeed(templateId?: string): TaskFeed {
  const { status, epoch, expireSession } = useIdentity();

  const tracker = useMemo(
    () =>
      new TaskTracker({
        fetchTask: (taskId, signal) => api.getTask(taskId, signal),
        onUnauthorized: expireSession,
      }),
    // A new identity must get a fresh tracker; expireSession is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [epoch, expireSession],
  );

  const snapshot = useSyncExternalStore(tracker.subscribe, tracker.getSnapshot, tracker.getSnapshot);

  const [state, setState] = useState<{ loading: boolean; error: string | null; cursor: string | null }>({
    loading: true,
    error: null,
    cursor: null,
  });
  const sequence = useRef(0);

  const load = useCallback(
    async (cursor: string | null) => {
      // 会话未就绪（加载中、建立失败、已失效）时不发请求：401 会触发 expireSession，
      // 而 epoch 变化又会重建 tracker，若无这道门会出现“401 → 重新加载 → 再 401”的循环。
      if (status !== "ready") {
        setState((current) => ({ ...current, loading: status === "loading" }));
        return;
      }
      const requestId = sequence.current + 1;
      sequence.current = requestId;
      setState((current) => ({ ...current, loading: true, error: null }));
      try {
        const page = await api.listTasks({ templateId, cursor: cursor ?? undefined, limit: PAGE_SIZE });
        if (requestId !== sequence.current) {
          return;
        }
        tracker.sync(page.items);
        setState({ loading: false, error: null, cursor: page.nextCursor });
      } catch (error) {
        if (requestId !== sequence.current) {
          return;
        }
        setState((current) => ({ ...current, loading: false, error: errorMessage(error) }));
      }
    },
    [templateId, tracker, status],
  );

  useEffect(() => {
    void load(null);
    return () => tracker.stop();
  }, [load, tracker]);

  const reload = useCallback(() => {
    void load(null);
  }, [load]);

  const loadMore = useCallback(() => {
    if (state.cursor) {
      void load(state.cursor);
    }
  }, [load, state.cursor]);

  return {
    tasks: snapshot.tasks,
    errors: snapshot.errors,
    paused: snapshot.paused,
    polling: snapshot.polling,
    loading: state.loading,
    error: state.error,
    hasMore: Boolean(state.cursor),
    reload,
    loadMore,
    tracker,
  };
}
