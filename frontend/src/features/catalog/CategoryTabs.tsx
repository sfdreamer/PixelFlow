"use client";

import type { Category } from "@/lib/api/types";
import styles from "./CategoryTabs.module.css";

interface Props {
  categories: Category[];
  activeId: string | null;
  onChange: (id: string) => void;
}

export function CategoryTabs({ categories, activeId, onChange }: Props) {
  return (
    <div className={styles.tabs} role="group" aria-label="模板分类">
      {categories.map((category) => {
        const active = category.id === activeId;
        return (
          <button
            key={category.id}
            type="button"
            className={active ? `${styles.tab} ${styles.active}` : styles.tab}
            aria-pressed={active}
            onClick={() => onChange(category.id)}
          >
            {category.name}
          </button>
        );
      })}
    </div>
  );
}
