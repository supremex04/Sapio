import { apiJson, apiRequest } from "../../shared/api/http";
import type { Engine, Visualization } from "./types";

const V = "/visualizations";

export const visualizeApi = {
  list: () => apiJson<Visualization[]>(V),
  get: (id: string) => apiJson<Visualization>(`${V}/${id}`),
  create: (request: string, engine: Engine = "AUTO", documentId?: string, context?: string) =>
    apiJson<Visualization>(V, { method: "POST", body: { request, engine, documentId, context } }),
  retry: (id: string) => apiJson<Visualization>(`${V}/${id}/retry`, { method: "POST" }),
  remove: (id: string) => apiRequest(`${V}/${id}`, { method: "DELETE" }),
};
