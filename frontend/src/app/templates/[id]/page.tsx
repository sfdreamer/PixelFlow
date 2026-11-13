"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError, errorMessage, isAbortError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Template } from "@/lib/api/types";
import { TaskCard } from "@/features/tasks/TaskCard";
import { useTaskFeed } from "@/features/tasks/useTaskFeed";
import { UploadSlots } from "@/features/upload/UploadSlots";
import { useUploadSlots } from "@/features/upload/useUploadSlots";
import styles from "./detail.module.css";

type LoadState = "loading" | "ready" | "notfound" | "error";
type SubmitState = "IDLE" | "SUBMITTING" | "UNCERTAIN";

const SUBMIT_TIMEOUT_MS = 20_000;

export default function TemplateDetailPage() {
  const params = useParams<{ id: string }>();
  const templateId = typeof params?.id === "string" ? params.id : "";

  const [template, setTemplate] = useState<Template | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState<string | null>(null);

  const [submitState, setSubmitState] = useState<SubmitState>("IDLE");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const feed = useTaskFeed(templateId);
  const upload = useUploadSlots(template);
  const submitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadTemplate = useCallback(async () => {
    if (!templateId) {
      setLoadState("notfound");
      return;
    }
    setLoadState("loading");
    setLoadError(null);
    try {
      const result = await api.getTemplate(templateId);
      setTemplate(result);
      setLoadState("ready");
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setLoadState("notfound");
      } else {
        setLoadError(errorMessage(error));
        setLoadState("error");
      }
    }
  }, [templateId]);

  useEffect(() => {
    void loadTemplate();
  }, [loadTemplate]);

  useEffect(
    () => () => {
      if (submitTimer.current) {
        clearTimeout(submitTimer.current);
      }
    },
    [],
  );

  const submit = useCallback(async () => {
    if (!template || !upload.canSubmit || submitState === "SUBMITTING") {
      return;
    }
    setSubmitState("SUBMITTING");
    setSubmitError(null);
    setNotice(null);

    // A timeout means the acceptance is unknown: never auto-resend the POST.
    const controller = new AbortController();
    submitTimer.current = setTimeout(() => controller.abort(), SUBMIT_TIMEOUT_MS);

    try {
      const task = await api.createTask(
        { templateId: template.id, assetIds: upload.assetIds },
        controller.signal,
      );
      feed.tracker.upsert(task);
      setSubmitState("IDLE");
      setNotice("任务已受理，后台开始制作。已上传的素材会保留，可再次提交创建新任务。");
    } catch (error) {
      if (isAbortError(error) || (error instanceof ApiError && error.status === 0)) {
        setSubmitState("UNCERTAIN");
        setSubmitError("提交结果暂未确认，请刷新历史确认");
      } else {
        setSubmitState("IDLE");
        setSubmitError(errorMessage(error));
      }
    } finally {
      if (submitTimer.current) {
        clearTimeout(submitTimer.current);
        submitTimer.current = null;
      }
    }
  }, [template, upload.canSubmit, upload.assetIds, submitState, feed.tracker]);

  if (loadState === "loading") {
    return (
      <div className={styles.page}>
        <div className={styles.skeleton} aria-busy="true" aria-label="正在加载模板">
          <div className={styles.skeletonCover} />
          <div className={styles.skeletonLine} />
          <div className={styles.skeletonLineShort} />
        </div>
      </div>
    );
  }

  if (loadState === "notfound") {
    return (
      <div className={styles.page}>
        <div className={styles.emptyState} role="alert">
          <h1 className={styles.emptyTitle}>模板不存在或已下架</h1>
          <p className={styles.emptyText}>请返回模板库选择其他模板。</p>
          <Link className={styles.backLink} href="/">
            返回模板库
          </Link>
        </div>
      </div>
    );
  }

  if (loadState === "error" || !template) {
    return (
      <div className={styles.page}>
        <div className={styles.emptyState} role="alert">
          <h1 className={styles.emptyTitle}>模板加载失败</h1>
          <p className={styles.emptyText}>{loadError}</p>
          <button type="button" className={styles.retryButton} onClick={() => void loadTemplate()}>
            重新加载
          </button>
        </div>
      </div>
    );
  }

  const disabled = submitState === "SUBMITTING" || upload.uploading || upload.canSubmit === false;

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <Link href="/" className={styles.backLink}>
          ← 返回模板库
        </Link>
        <span className={styles.crumb}>{template.title}</span>
      </div>

      <div className={styles.layout}>
        <section className={styles.panel} aria-labelledby="template-title">
          <div className={styles.coverWrap}>
            <img className={styles.cover} src={template.coverUrl} alt={`${template.title} 示例封面`} />
          </div>

          <div className={styles.panelHead}>
            <h1 id="template-title" className={styles.title}>
              {template.title}
            </h1>
            <p className={styles.description}>{template.description}</p>
            <p className={styles.requirement}>
              需要 {template.requiredImageCount} 张图片 · 支持 JPEG / PNG · 单张不超过 5 MB
            </p>
          </div>

          <div className={styles.uploadSection}>
            <h2 className={styles.sectionTitle}>上传素材</h2>
            <UploadSlots
              slots={upload.slots}
              onChoose={(index, file) => void upload.choose(index, file)}
              onRemove={upload.remove}
              disabled={submitState === "SUBMITTING"}
            />
          </div>

          <div className={styles.submitArea}>
            <button
              type="button"
              className={styles.submit}
              onClick={() => void submit()}
              disabled={disabled}
              aria-busy={submitState === "SUBMITTING"}
            >
              {submitState === "SUBMITTING" ? "提交中…" : "开始制作"}
            </button>

            {!upload.canSubmit && submitState !== "SUBMITTING" ? (
              <p className={styles.hint}>
                所有 {template.requiredImageCount} 个上传位都就绪后才能提交。
              </p>
            ) : null}

            {submitError ? (
              <div className={styles.submitError} role="alert">
                <p>{submitError}</p>
                {submitState === "UNCERTAIN" ? (
                  <div className={styles.uncertainActions}>
                    <Link href="/history" className={styles.inlineLink}>
                      查看历史确认
                    </Link>
                    <button
                      type="button"
                      className={styles.secondaryButton}
                      onClick={() => {
                        upload.slots.forEach((slot) => upload.remove(slot.index));
                        setSubmitState("IDLE");
                        setSubmitError(null);
                      }}
                    >
                      清空并重新填写
                    </button>
                  </div>
                ) : null}
              </div>
            ) : null}

            {notice ? (
              <p className={styles.notice} role="status">
                {notice}
              </p>
            ) : null}
          </div>
        </section>

        <section className={styles.history} aria-labelledby="history-title">
          <header className={styles.historyHead}>
            <h2 id="history-title" className={styles.sectionTitle}>
              我的制作记录
            </h2>
            <div className={styles.historyMeta}>
              {feed.polling ? <span className={styles.polling}>自动更新中</span> : null}
              <button type="button" className={styles.refresh} onClick={feed.reload} disabled={feed.loading}>
                {feed.loading ? "刷新中…" : "刷新"}
              </button>
            </div>
          </header>

          {feed.error ? (
            <div className={styles.feedError} role="alert">
              <span>{feed.error}</span>
              <button type="button" className={styles.refresh} onClick={feed.reload}>
                重试
              </button>
            </div>
          ) : null}

          {feed.tasks.length === 0 && !feed.loading ? (
            <div className={styles.emptyHistory}>
              <p className={styles.emptyText}>这个模板还没有制作记录，上传图片后提交第一个任务。</p>
            </div>
          ) : (
            <div className={styles.cards}>
              {feed.tasks.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  queryError={feed.errors[task.id]}
                  onRetry={(id) => feed.tracker.retry(id)}
                />
              ))}
            </div>
          )}

          {feed.hasMore ? (
            <button type="button" className={styles.loadMore} onClick={feed.loadMore} disabled={feed.loading}>
              加载更多
            </button>
          ) : null}
        </section>
      </div>
    </div>
  );
}
