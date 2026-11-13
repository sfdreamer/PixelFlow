import type { Stage, StepStatus, TaskStatus } from "@/lib/api/types";

export const stageLabels: Record<Stage, string> = {
  ASSET_CHECK: "素材检查",
  GENERATE: "模拟生成",
  FINALIZE: "结果整理",
};

export const statusLabels: Record<TaskStatus, string> = {
  QUEUED: "排队中",
  RUNNING: "制作中",
  SUCCEEDED: "已完成",
  FAILED: "制作失败",
};

export const stepLabels: Record<StepStatus, string> = {
  PENDING: "等待",
  RUNNING: "进行中",
  SUCCEEDED: "完成",
  FAILED: "失败",
  SKIPPED: "已跳过",
};

export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

export function formatFullTime(iso: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}
