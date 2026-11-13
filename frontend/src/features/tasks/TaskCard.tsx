"use client";

import { useState } from "react";

import type { Task } from "@/lib/api/types";
import type { TaskQueryError } from "./TaskTracker";
import { formatFullTime, formatTime, stageLabels, statusLabels, stepLabels } from "./labels";
import styles from "./TaskCard.module.css";

interface Props {
  task: Task;
  queryError?: TaskQueryError;
  onRetry?: (taskId: string) => void;
  showTemplate?: boolean;
}

function ResultPreview({ task }: { task: Task }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <div className={styles.resultFallback} role="status">
        结果预览不可用，可稍后刷新重试
      </div>
    );
  }
  return (
    // The authenticated same-origin URL is loaded directly; the Next image optimizer would drop the cookie.
    <img
      className={styles.result}
      src={task.result?.previewUrl ?? `/api/v1/tasks/${task.id}/result`}
      alt={`${task.templateTitle} 的制作结果`}
      onError={() => setFailed(true)}
    />
  );
}

export function TaskCard({ task, queryError, onRetry, showTemplate = false }: Props) {
  const running = task.status === "RUNNING";
  const queued = task.status === "QUEUED";
  const succeeded = task.status === "SUCCEEDED";

  return (
    <article
      className={styles.card}
      aria-label={`${task.templateTitle}，${statusLabels[task.status]}`}
      data-status={task.status}
    >
      <header className={styles.head}>
        <time className={styles.time} dateTime={task.createdAt} title={formatFullTime(task.createdAt)}>
          {formatTime(task.createdAt)}
        </time>
        <span className={`${styles.chip} ${styles[task.status.toLowerCase()]}`}>{statusLabels[task.status]}</span>
      </header>

      {showTemplate ? <p className={styles.template}>{task.templateTitle}</p> : null}

      <ol className={styles.steps} aria-label="执行阶段">
        {task.steps.map((step) => (
          <li key={step.stage} className={styles.step} data-status={step.status}>
            <span className={styles.stepDot} aria-hidden="true" />
            <span className={styles.stepName}>{stageLabels[step.stage]}</span>
            <span className={styles.stepStatus}>{stepLabels[step.status]}</span>
          </li>
        ))}
      </ol>

      <div className={styles.body}>
        {queued ? (
          <div className={styles.placeholder}>
            <span className={styles.spinner} aria-hidden="true" />
            <span>已排队，等待后台领取任务</span>
          </div>
        ) : null}

        {running ? (
          <div className={styles.placeholder} aria-live="polite">
            <span className={`${styles.spinner} ${styles.spinnerActive}`} aria-hidden="true" />
            <span>正在{task.currentStage ? stageLabels[task.currentStage] : "处理"}…</span>
          </div>
        ) : null}

        {succeeded && task.result ? <ResultPreview task={task} /> : null}

        {task.status === "FAILED" ? (
          <p className={styles.failure}>
            <span className={styles.failureStage}>
              {task.error?.failedStage ? `${stageLabels[task.error.failedStage]}阶段` : "执行中断"}
            </span>
            {task.error?.message ?? "任务执行失败"}
            {task.error?.code ? <span className={styles.code}>{task.error.code}</span> : null}
          </p>
        ) : null}
      </div>

      {queryError ? (
        <div className={styles.queryError} role="status">
          <span>{queryError.message}</span>
          {queryError.retryable && onRetry ? (
            <button type="button" className={styles.retry} onClick={() => onRetry(task.id)}>
              恢复查询
            </button>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}
