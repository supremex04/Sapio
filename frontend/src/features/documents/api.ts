import { apiJson, apiRequest } from "../../shared/api/http";
import type { PageResponse } from "../../shared/api/types";
import { filenameFromDisposition } from "../../shared/lib/download";
import type { DocumentDto, DocumentResult } from "./types";

export const documentsApi = {
  upload(file: File): Promise<DocumentDto> {
    const body = new FormData();
    body.append("file", file);
    return apiJson<DocumentDto>("/documents", { method: "POST", body });
  },
  list: (page = 0, size = 20) => apiJson<PageResponse<DocumentDto>>(`/documents?page=${page}&size=${size}`),
  get: (id: string) => apiJson<DocumentDto>(`/documents/${id}`),
  retry: (id: string) => apiJson<DocumentDto>(`/documents/${id}/retry`, { method: "POST" }),
  result: (id: string) => apiJson<DocumentResult>(`/documents/${id}/result`),
  async download(id: string, fallbackName: string): Promise<{ blob: Blob; filename: string }> {
    const response = await apiRequest(`/documents/${id}/result/download`);
    return {
      blob: await response.blob(),
      filename: filenameFromDisposition(response.headers.get("Content-Disposition"), fallbackName),
    };
  },
  remove: (id: string) => apiRequest(`/documents/${id}`, { method: "DELETE" }),
};
