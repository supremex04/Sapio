import { apiJson, apiRequest } from "../../shared/api/http";
import type { Visualization } from "./types";

const V = "/visualizations";

export const visualizeApi = {
  list: () => apiJson<Visualization[]>(V),
  get: (id: string) => apiJson<Visualization>(`${V}/${id}`),
  create: (request: string, documentId?: string, context?: string) =>
    apiJson<Visualization>(V, { method: "POST", body: { request, documentId, context } }),
  retry: (id: string) => apiJson<Visualization>(`${V}/${id}/retry`, { method: "POST" }),
  remove: (id: string) => apiRequest(`${V}/${id}`, { method: "DELETE" }),
};
