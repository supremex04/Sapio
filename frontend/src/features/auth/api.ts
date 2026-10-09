import { apiJson, apiRequest } from "../../shared/api/http";
import type { AuthResponse, LoginInput, RegisterInput } from "./types";

// Auth endpoints never send the access token and must not trigger the refresh-and-retry loop.
export const authApi = {
  register: (input: RegisterInput) =>
    apiJson<AuthResponse>("/auth/register", { method: "POST", body: input, auth: false }),
  login: (input: LoginInput) => apiJson<AuthResponse>("/auth/login", { method: "POST", body: input, auth: false }),
  refresh: () => apiJson<AuthResponse>("/auth/refresh", { method: "POST", auth: false }),
  logout: () => apiRequest("/auth/logout", { method: "POST", auth: false }),
};
