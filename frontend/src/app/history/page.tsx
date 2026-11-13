"use client";

import Link from "next/link";

import { TaskCard } from "@/features/tasks/TaskCard";
import { useTaskFeed } from "@/features/tasks/useTaskFeed";
import { useIdentity } from "@/features/identity/IdentityProvider";
import styles from "./history.module.css";

export default function HistoryPage() {
  const feed = useTaskFeed();
  const { status, session } = useIdentity();
  const lead =
    status === "expired"
      ? "会话已失效，重新建立会话后可以继续查看本浏览器的记录。"
      : session
        ? `${session.displayName} 的全部任务，按创建时间从新到旧排列。`
        : "正在恢复身份…";

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          <p className={styles.eyebrow}>History</p>
          <h1 className={styles.title}>我的制作历史</h1>
          <p className={styles.lead}>
            {lead}
            未完成的任务会持续更新，离开页面不会中断后台制作。
          </p>
        </div>
        <div className={styles.actions}>
          {feed.polling ? <span className={styles.polling}>自动更新中</span> : null}
          <button type="button" className={styles.refresh} onClick={feed.reload} disabled={feed.loading}>
            {feed.loading ? "刷新中…" : "刷新"}
          </button>
          <Link href="/" className={styles.link}>
            去选模板
          </Link>
        </div>
      </header>

      {feed.error ? (
        <div className={styles.error} role="alert">
          <span>历史加载失败：{feed.error}</span>
          <button type="button" className={styles.refresh} onClick={feed.reload}>
            重新加载
          </button>
        </div>
      ) : null}

      {feed.loading && feed.tasks.length === 0 ? (
        <div className={styles.grid} aria-busy="true" aria-label="正在加载历史">
          {Array.from({ length: 3 }, (_, index) => (
            <div key={index} className={styles.skeleton} aria-hidden="true" />
          ))}
        </div>
      ) : null}

      {!feed.loading && feed.tasks.length === 0 && !feed.error ? (
        <div className={styles.empty}>
          <h2 className={styles.emptyTitle}>还没有制作记录</h2>
          <p className={styles.emptyText}>选择一个模板，上传图片并提交任务后，这里会显示执行状态和结果。</p>
          <Link href="/" className={styles.link}>
            浏览模板
          </Link>
        </div>
      ) : null}

      <div className={styles.grid}>
        {feed.tasks.map((task) => (
          <TaskCard
            key={task.id}
            task={task}
            showTemplate
            queryError={feed.errors[task.id]}
            onRetry={(id) => feed.tracker.retry(id)}
          />
        ))}
      </div>

      {feed.hasMore ? (
        <button type="button" className={styles.loadMore} onClick={feed.loadMore} disabled={feed.loading}>
          {feed.loading ? "加载中…" : "加载更多"}
        </button>
      ) : null}

      <p className={styles.footnote}>
        当前使用匿名身份，记录只属于本浏览器。用另一个浏览器或隐私窗口访问可验证历史互不可见。
      </p>
    </div>
  );
}
