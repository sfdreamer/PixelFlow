/** Types generated from docs/openapi.json. Field names and enums must match the contract. */

export type Stage = "ASSET_CHECK" | "GENERATE" | "FINALIZE";
export type TaskStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type StepStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
export type AcceptedMimeType = "image/jpeg" | "image/png";

export interface Session {
  userId: string;
  displayName: string;
  expiresAt: string;
}

export interface Category {
  id: string;
  name: string;
  sortOrder: number;
}

export interface Template {
  id: string;
  categoryId: string;
  title: string;
  description: string;
  coverUrl: string;
  requiredImageCount: number;
  acceptedMimeTypes: AcceptedMimeType[];
  maxFileSizeBytes: number;
}

export interface Asset {
  id: string;
  templateId: string;
  originalName: string;
  mimeType: AcceptedMimeType;
  sizeBytes: number;
  width: number;
  height: number;
  previewUrl: string;
  createdAt: string;
}

export interface Step {
  stage: Stage;
  status: StepStatus;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface Result {
  previewUrl: string;
  mimeType: AcceptedMimeType;
  width: number;
  height: number;
}

export interface TaskError {
  code: string;
  message: string;
  failedStage: Stage | null;
}

export interface Task {
  id: string;
  templateId: string;
  templateTitle: string;
  status: TaskStatus;
  currentStage: Stage | null;
  steps: Step[];
  result: Result | null;
  error: TaskError | null;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  revision: number;
}

export interface TaskPage {
  items: Task[];
  nextCursor: string | null;
}

export interface CategoryList {
  items: Category[];
}

export interface TemplateList {
  items: Template[];
}

export interface ErrorDetail {
  field: string;
  message: string;
}

export interface ErrorBody {
  code: string;
  message: string;
  traceId: string;
  details: ErrorDetail[];
}

export interface CreateTaskInput {
  templateId: string;
  assetIds: string[];
  simulation?: {
    failAt?: Stage | null;
    stageDelayMs?: Partial<Record<Stage, number>>;
  };
}
