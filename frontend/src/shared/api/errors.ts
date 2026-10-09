/** Mirrors the backend's ApiError body (see docs/specs/api-conventions.md). */
export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  path?: string;
  timestamp?: string;
  fieldErrors?: { field: string; message: string }[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = Object.fromEntries((body.fieldErrors ?? []).map((f) => [f.field, f.message]));
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    try {
      const body = (await response.json()) as ApiErrorBody;
      if (body && typeof body.code === "string") return new ApiError(body);
    } catch {
      // not JSON - fall through
    }
    return new ApiError({ status: response.status, code: "HTTP_" + response.status, message: response.statusText || "Request failed" });
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return "Cannot reach the server. Check your connection.";
  return "Something went wrong.";
}
