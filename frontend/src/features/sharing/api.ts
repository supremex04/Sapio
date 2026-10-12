import { apiJson, apiRequest } from "../../shared/api/http";
import type { PageResponse } from "../../shared/api/types";
import type { Category, ExploreSort, ImportResult, MyShare, ShareComment, ShareDetail, ShareSummary, VoteValue, Votes } from "./types";

export const sharingApi = {
  categories: () => apiJson<Category[]>("/explore/categories"),
  explore(params: { category?: string | null; q?: string; sort: ExploreSort; page?: number; size?: number }) {
    const query = new URLSearchParams({ sort: params.sort, page: String(params.page ?? 0), size: String(params.size ?? 24) });
    if (params.category) query.set("category", params.category);
    if (params.q?.trim()) query.set("q", params.q.trim());
    return apiJson<PageResponse<ShareSummary>>(`/explore?${query}`);
  },
  detail: (shareId: string) => apiJson<ShareDetail>(`/explore/${shareId}`),
  /** Copies the shared document into the viewer's library (or returns the copy made earlier). */
  import: (shareId: string) => apiJson<ImportResult>(`/explore/${shareId}/import`, { method: "POST" }),
  vote: (shareId: string, value: VoteValue) => apiJson<Votes>(`/explore/${shareId}/vote`, { method: "PUT", body: { value } }),
  comments: (shareId: string, page = 0, size = 20) =>
    apiJson<PageResponse<ShareComment>>(`/explore/${shareId}/comments?page=${page}&size=${size}`),
  comment: (shareId: string, body: string) =>
    apiJson<ShareComment>(`/explore/${shareId}/comments`, { method: "POST", body: { body } }),
  deleteComment: (shareId: string, commentId: string) =>
    apiRequest(`/explore/${shareId}/comments/${commentId}`, { method: "DELETE" }),
  mine: () => apiJson<MyShare[]>("/shares"),
  share: (documentId: string, category: string, description: string) =>
    apiJson<MyShare>(`/documents/${documentId}/share`, { method: "PUT", body: { category, description } }),
  unshare: (documentId: string) => apiRequest(`/documents/${documentId}/share`, { method: "DELETE" }),
};
