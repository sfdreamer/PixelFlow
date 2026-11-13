"use client";

import Link from "next/link";

import type { Template } from "@/lib/api/types";
import styles from "./TemplateGrid.module.css";

interface Props {
  templates: Template[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}

function mimeLabel(template: Template): string {
  return template.acceptedMimeTypes.map((type) => (type === "image/png" ? "PNG" : "JPEG")).join(" / ");
}

export function TemplateGrid({ templates, loading, error, onRetry }: Props) {
  if (loading) {
    return (
      <ul className={styles.grid} aria-busy="true" aria-label="正在加载模板">
        {Array.from({ length: 4 }, (_, index) => (
          <li key={index} className={styles.skeleton} aria-hidden="true">
            <div className={styles.skeletonImage} />
            <div className={styles.skeletonLine} />
            <div className={styles.skeletonLineShort} />
          </li>
        ))}
      </ul>
    );
  }

  if (error) {
    return (
      <div className={styles.state} role="alert">
        <p className={styles.stateTitle}>模板加载失败</p>
        <p className={styles.stateText}>{error}</p>
        <button type="button" className={styles.retry} onClick={onRetry}>
          重新加载
        </button>
      </div>
    );
  }

  if (templates.length === 0) {
    return (
      <div className={styles.state}>
        <p className={styles.stateTitle}>该分类下暂无模板</p>
        <p className={styles.stateText}>请选择其他分类，稍后再试。</p>
      </div>
    );
  }

  return (
    <ul className={styles.grid}>
      {templates.map((template) => (
        <li key={template.id}>
          <Link href={`/templates/${template.id}`} className={styles.card}>
            <span className={styles.coverWrap}>
                <img className={styles.cover} src={template.coverUrl} alt={`${template.title} 示例封面`} loading="lazy" />
              <span className={styles.badge}>{template.requiredImageCount} 张图片</span>
            </span>
            <span className={styles.body}>
              <span className={styles.title}>{template.title}</span>
              <span className={styles.meta}>{mimeLabel(template)} · 单张上限 5 MB</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
