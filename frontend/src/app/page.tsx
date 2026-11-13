"use client";

import { useCallback, useEffect, useState } from "react";

import { errorMessage } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Category, Template } from "@/lib/api/types";
import { CategoryTabs } from "@/features/catalog/CategoryTabs";
import { TemplateGrid } from "@/features/catalog/TemplateGrid";
import styles from "./page.module.css";

export default function HomePage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [templateError, setTemplateError] = useState<string | null>(null);
  const [loadingTemplates, setLoadingTemplates] = useState(true);
  const [categoriesLoading, setCategoriesLoading] = useState(true);

  const loadCategories = useCallback(async () => {
    setCategoriesLoading(true);
    setCategoryError(null);
    try {
      const result = await api.listCategories();
      setCategories(result.items);
      setActiveId((current) => current ?? result.items[0]?.id ?? null);
    } catch (error) {
      setCategoryError(errorMessage(error));
    } finally {
      setCategoriesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  const loadTemplates = useCallback(async (categoryId: string) => {
    setLoadingTemplates(true);
    setTemplateError(null);
    try {
      const result = await api.listTemplates(categoryId);
      setTemplates(result.items);
    } catch (error) {
      setTemplateError(errorMessage(error));
      setTemplates([]);
    } finally {
      setLoadingTemplates(false);
    }
  }, []);

  useEffect(() => {
    if (activeId) {
      void loadTemplates(activeId);
    }
  }, [activeId, loadTemplates]);

  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <p className={styles.eyebrow}>模拟素材制作 · Simulated asset studio</p>
        <h1 className={styles.title}>选一个模板，上传图片，剩下的交给后台</h1>
        <p className={styles.lead}>
          提交后立即返回任务，制作在 Java 后台按「素材检查 → 模拟生成 → 结果整理」顺序执行。你可以继续浏览、
          连续提交，稍后在历史中查看结果。
        </p>
      </section>

      {categoryError ? (
        <div className={styles.error} role="alert">
          <span>分类加载失败：{categoryError}</span>
          <button type="button" className={styles.retry} onClick={() => void loadCategories()}>
            重新加载
          </button>
        </div>
      ) : null}

      {categoriesLoading ? (
        <div className={styles.tabsSkeleton} aria-hidden="true">
          <span />
          <span />
        </div>
      ) : (
        <CategoryTabs
          categories={categories}
          activeId={activeId}
          onChange={(id) => {
            if (id !== activeId) {
              setActiveId(id);
            }
          }}
        />
      )}

      <TemplateGrid
        templates={templates}
        loading={loadingTemplates && !templateError}
        error={templateError}
        onRetry={() => activeId && void loadTemplates(activeId)}
      />
    </div>
  );
}
