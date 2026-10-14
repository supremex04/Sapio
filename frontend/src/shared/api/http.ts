import { ApiError } from "./errors";

export const API_BASE = import.meta.env.VITE_API_BASE ?? "/api";

/**
 * The access token lives only in memory (never localStorage) to limit XSS exposure. After a page
 * reload it is recovered via the HttpOnly refresh cookie.
 */
let accessToken: string | null = null;
let refreshHandler: (() => Promise<string | null>) | null = null;
let inflightRefresh: Promise<string | null> | null = null;

export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => {
    accessToken = token;
  },
};

/** Registered by the auth feature; called when an access token is rejected or missing. */
export function setRefreshHandler(handler: () => Promise<string | null>): void {
  refreshHandler = handler;
}

/** Runs the registered refresh handler, sharing one in-flight call between all callers. */
export function refreshSession(): Promise<string | null> {
  if (!refreshHandler) return Promise.resolve(null);
  // Concurrent 401s share a single refresh call (refresh tokens rotate, so parallel calls would race).
  inflightRefresh ??= refreshHandler().finally(() => {
    inflightRefresh = null;
  });
  return inflightRefresh;
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: BodyInit | object | null;
  /** Attach the access token and retry once after refreshing on 401. Default true. */
  auth?: boolean;
}

/** Low-level request: returns the Response on 2xx, throws ApiError otherwise. */
export async function apiRequest(path: string, options: RequestOptions = {}): Promise<Response> {
  const { auth = true, body, headers, ...init } = options;
  const isRawBody = body instanceof FormData || body instanceof Blob || typeof body === "string";

  const send = (token: string | null) => {
    const h = new Headers(headers);
    if (body != null && !isRawBody) h.set("Content-Type", "application/json");
    if (auth && token) h.set("Authorization", `Bearer ${token}`);
    return fetch(API_BASE + path, {
      ...init,
      headers: h,
      credentials: "include",
      body: body == null ? undefined : isRawBody ? (body as BodyInit) : JSON.stringify(body),
    });
  };

  let response = await send(tokenStore.get());
  if (response.status === 401 && auth) {
    const refreshed = await refreshSession();
    if (refreshed) response = await send(refreshed);
  }
  if (!response.ok) throw await ApiError.fromResponse(response);
  return response;
}

export async function apiJson<T>(path: string, options?: RequestOptions): Promise<T> {
  const response = await apiRequest(path, options);
  return (await response.json()) as T;
}
