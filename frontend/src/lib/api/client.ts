import type { ErrorBody, ErrorDetail } from "./types";

/** Error raised for any non-2xx response; carries the contract code and trace id. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId: string;
  readonly details: ErrorDetail[];

  constructor(status: number, code: string, message: string, traceId = "", details: ErrorDetail[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.traceId = traceId;
    this.details = details;
  }
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registered once by the identity provider: any 401 stops polling and asks for a new session. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === "AbortError") ||
    (error instanceof Error && error.name === "AbortError")
  );
}

async function readError(response: Response): Promise<ApiError> {
  let body: Partial<ErrorBody> | null = null;
  try {
    body = (await response.json()) as Partial<ErrorBody>;
  } catch {
    body = null;
  }
  return new ApiError(
    response.status,
    body?.code ?? `HTTP_${response.status}`,
    body?.message ?? `请求失败（HTTP ${response.status}）`,
    body?.traceId ?? "",
    body?.details ?? [],
  );
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw new ApiError(0, "NETWORK_ERROR", "网络请求失败，请检查连接后重试");
  }

  if (!response.ok) {
    const apiError = await readError(response);
    if (response.status === 401) {
      unauthorizedHandler?.();
    }
    throw apiError;
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "发生未知错误";
}
