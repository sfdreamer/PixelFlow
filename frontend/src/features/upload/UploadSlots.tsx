"use client";

import type { ChangeEvent } from "react";

import type { UploadSlot } from "./useUploadSlots";
import styles from "./UploadSlots.module.css";

interface Props {
  slots: UploadSlot[];
  onChoose: (index: number, file: File) => void;
  onRemove: (index: number) => void;
  disabled?: boolean;
}

const statusText: Record<UploadSlot["status"], string> = {
  EMPTY: "待上传",
  UPLOADING: "上传中…",
  READY: "已就绪",
  ERROR: "上传失败",
};

export function UploadSlots({ slots, onChoose, onRemove, disabled = false }: Props) {
  return (
    <ul className={styles.list}>
      {slots.map((slot) => {
        const slotNumber = slot.index + 1;
        const inputId = `upload-slot-${slot.index}`;
        return (
          <li key={slot.index} className={styles.slot} data-status={slot.status}>
            <div className={styles.head}>
              <label className={styles.label} htmlFor={inputId}>
                上传位 {slotNumber}
              </label>
              <span className={styles.status} aria-live="polite">
                {statusText[slot.status]}
              </span>
            </div>

            <div className={styles.preview}>
              {slot.previewUrl ? (
                    <img className={styles.image} src={slot.previewUrl} alt={`上传位 ${slotNumber} 预览`} />
              ) : (
                <span className={styles.placeholder} aria-hidden="true">
                  <span className={styles.placeholderIcon}>＋</span>
                  <span className={styles.placeholderText}>JPEG / PNG，单张不超过 5 MB</span>
                </span>
              )}
              {slot.status === "UPLOADING" ? <span className={styles.overlay}>正在校验并上传…</span> : null}
            </div>

            <div className={styles.actions}>
              <label className={`${styles.button} ${styles.primaryAction}`}>
                {slot.status === "READY" ? "替换图片" : "选择图片"}
                <input
                  id={inputId}
                  type="file"
                  accept="image/jpeg,image/png"
                  className={styles.input}
                  disabled={disabled}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    const file = event.target.files?.[0];
                    event.target.value = "";
                    if (file) {
                      onChoose(slot.index, file);
                    }
                  }}
                />
              </label>
              {slot.status !== "EMPTY" ? (
                <button
                  type="button"
                  className={styles.button}
                  aria-label={`移除上传位 ${slotNumber} 的图片`}
                  onClick={() => onRemove(slot.index)}
                  disabled={disabled}
                >
                  移除
                </button>
              ) : null}
            </div>

            {slot.fileName ? <p className={styles.fileName}>{slot.fileName}</p> : null}
            {slot.error ? (
              <p className={styles.error} role="alert">
                {slot.error}
              </p>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
