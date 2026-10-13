import { apiJson, apiRequest } from "../../shared/api/http";
import type { PageResponse } from "../../shared/api/types";
import { filenameFromDisposition } from "../../shared/lib/download";
import type { DocumentContent, DocumentDto, DocumentResult, NoteStyle, SectionNotes } from "./types";

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
  /** The whole document in reading order (tree sections with text and figures in place). */
  content: (id: string) => apiJson<DocumentContent>(`/documents/${id}/content`),
  /** A signed URL an audio / video element can stream (with seeking) without an Authorization header. */
  playback: (id: string) => apiJson<{ url: string; expiresAt: string }>(`/documents/${id}/playback`),
  /** The uploaded file itself (fetched with auth, shown via an object URL). */
  async original(id: string): Promise<Blob> {
    return (await apiRequest(`/documents/${id}/original`)).blob();
  },
  /** AI rewrite of one section: EXPAND (in depth) or REVISION (condensed). Generated once, then cached. */
  notes: (id: string, nodeId: string, style: NoteStyle) =>
    apiJson<SectionNotes>(`/documents/${id}/nodes/${nodeId}/notes?style=${style}`),
};
