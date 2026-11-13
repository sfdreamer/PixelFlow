"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError, errorMessage, isAbortError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Template } from "@/lib/api/types";

export type SlotStatus = "EMPTY" | "UPLOADING" | "READY" | "ERROR";

export interface UploadSlot {
  index: number;
  status: SlotStatus;
  assetId: string | null;
  previewUrl: string | null;
  fileName: string | null;
  error: string | null;
  /** Increments on every selection so a late response for an older file is discarded. */
  version: number;
}

function emptySlot(index: number): UploadSlot {
  return { index, status: "EMPTY", assetId: null, previewUrl: null, fileName: null, error: null, version: 0 };
}

function formatSize(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * One state machine per upload slot. Selecting a new file aborts the previous request, revokes the
 * previous Object URL and bumps the slot version; a response whose version is stale is ignored, so a
 * replaced file can never reappear. Object URLs are only for instant preview; the submitted value is
 * always the server-side assetId.
 */
export function useUploadSlots(template: Template | null) {
  const [slots, setSlots] = useState<UploadSlot[]>([]);
  const versions = useRef<number[]>([]);
  const controllers = useRef<Array<AbortController | null>>([]);
  const previews = useRef<Array<string | null>>([]);
  const objectUrls = useRef<Set<string>>(new Set());
  const templateKey = template?.id ?? null;
  const requiredCount = template?.requiredImageCount ?? 0;

  const revoke = useCallback((url: string | null) => {
    if (url && objectUrls.current.has(url)) {
      URL.revokeObjectURL(url);
      objectUrls.current.delete(url);
    }
  }, []);

  useEffect(() => {
    versions.current = Array.from({ length: requiredCount }, () => 0);
    controllers.current = Array.from({ length: requiredCount }, () => null);
    previews.current = Array.from({ length: requiredCount }, () => null);
    setSlots(Array.from({ length: requiredCount }, (_, index) => emptySlot(index)));
  }, [templateKey, requiredCount]);

  useEffect(() => {
    const activeControllers = controllers.current;
    const urls = objectUrls.current;
    return () => {
      for (const controller of activeControllers) {
        controller?.abort();
      }
      for (const url of urls) {
        URL.revokeObjectURL(url);
      }
      urls.clear();
    };
  }, []);

  const patchSlot = useCallback((index: number, patch: Partial<UploadSlot>) => {
    setSlots((current) => current.map((slot) => (slot.index === index ? { ...slot, ...patch } : slot)));
  }, []);

  const choose = useCallback(
    async (index: number, file: File) => {
      if (!template || index < 0 || index >= template.requiredImageCount) {
        return;
      }
      const version = (versions.current[index] ?? 0) + 1;
      versions.current[index] = version;

      const reject = (message: string) => {
        revoke(previews.current[index] ?? null);
        previews.current[index] = null;
        patchSlot(index, {
          status: "ERROR",
          error: message,
          assetId: null,
          previewUrl: null,
          fileName: file.name,
          version,
        });
      };

      if (file.size <= 0) {
        reject("文件内容为空");
        return;
      }
      if (file.size > template.maxFileSizeBytes) {
        reject(`文件超过 ${formatSize(template.maxFileSizeBytes)} 限制`);
        return;
      }
      const type = file.type.toLowerCase();
      if (type && !template.acceptedMimeTypes.some((accepted) => accepted === type)) {
        reject("仅支持 JPEG 或 PNG 图片");
        return;
      }

      controllers.current[index]?.abort();
      const controller = new AbortController();
      controllers.current[index] = controller;

      revoke(previews.current[index] ?? null);
      const objectUrl = URL.createObjectURL(file);
      objectUrls.current.add(objectUrl);
      previews.current[index] = objectUrl;
      patchSlot(index, {
        status: "UPLOADING",
        assetId: null,
        previewUrl: objectUrl,
        fileName: file.name,
        error: null,
        version,
      });

      try {
        const asset = await api.uploadAsset(template.id, file, controller.signal);
        if (versions.current[index] !== version || controller.signal.aborted) {
          return;
        }
        revoke(objectUrl);
        previews.current[index] = asset.previewUrl;
        patchSlot(index, {
          status: "READY",
          assetId: asset.id,
          previewUrl: asset.previewUrl,
          fileName: asset.originalName,
          error: null,
        });
      } catch (error) {
        if (versions.current[index] !== version || isAbortError(error)) {
          return;
        }
        const message =
          error instanceof ApiError && error.status === 413
            ? `文件超过 ${formatSize(template.maxFileSizeBytes)} 限制`
            : errorMessage(error);
        revoke(objectUrl);
        previews.current[index] = null;
        patchSlot(index, { status: "ERROR", assetId: null, previewUrl: null, error: message });
      }
    },
    [template, patchSlot, revoke],
  );

  const remove = useCallback(
    (index: number) => {
      versions.current[index] = (versions.current[index] ?? 0) + 1;
      controllers.current[index]?.abort();
      controllers.current[index] = null;
      revoke(previews.current[index] ?? null);
      previews.current[index] = null;
      patchSlot(index, { ...emptySlot(index), version: versions.current[index] });
    },
    [patchSlot, revoke],
  );

  const assetIds = slots.map((slot) => slot.assetId).filter((id): id is string => Boolean(id));
  const allReady = slots.length > 0 && slots.every((slot) => slot.status === "READY" && slot.assetId);
  const uniqueIds = new Set(assetIds).size === slots.length;

  return {
    slots,
    choose,
    remove,
    assetIds,
    canSubmit: allReady && uniqueIds,
    uploading: slots.some((slot) => slot.status === "UPLOADING"),
  };
}
