import { apiRequest } from "./client";
import type {
  Asset,
  CategoryList,
  CreateTaskInput,
  Session,
  Task,
  TaskPage,
  Template,
  TemplateList,
} from "./types";

export const api = {
  ensureSession: (): Promise<Session> => apiRequest<Session>("/api/v1/session", { method: "POST" }),

  getSession: (): Promise<Session> => apiRequest<Session>("/api/v1/session"),

  listCategories: (): Promise<CategoryList> => apiRequest<CategoryList>("/api/v1/categories"),

  listTemplates: (categoryId?: string): Promise<TemplateList> =>
    apiRequest<TemplateList>(
      categoryId ? `/api/v1/templates?categoryId=${encodeURIComponent(categoryId)}` : "/api/v1/templates",
    ),

  getTemplate: (templateId: string): Promise<Template> =>
    apiRequest<Template>(`/api/v1/templates/${encodeURIComponent(templateId)}`),

  uploadAsset: (templateId: string, file: File, signal?: AbortSignal): Promise<Asset> => {
    const body = new FormData();
    body.append("templateId", templateId);
    body.append("file", file);
    return apiRequest<Asset>("/api/v1/assets", { method: "POST", body, signal });
  },

  createTask: (input: CreateTaskInput, signal?: AbortSignal): Promise<Task> =>
    apiRequest<Task>("/api/v1/tasks", { method: "POST", body: JSON.stringify(input), signal }),

  listTasks: (params: {
    templateId?: string;
    cursor?: string;
    limit?: number;
  }): Promise<TaskPage> => {
    const search = new URLSearchParams();
    if (params.templateId) search.set("templateId", params.templateId);
    if (params.cursor) search.set("cursor", params.cursor);
    if (params.limit) search.set("limit", String(params.limit));
    const query = search.toString();
    return apiRequest<TaskPage>(`/api/v1/tasks${query ? `?${query}` : ""}`);
  },

  getTask: (taskId: string, signal?: AbortSignal): Promise<Task> =>
    apiRequest<Task>(`/api/v1/tasks/${encodeURIComponent(taskId)}`, { signal }),

  taskResultUrl: (taskId: string): string => `/api/v1/tasks/${encodeURIComponent(taskId)}/result`,
};
