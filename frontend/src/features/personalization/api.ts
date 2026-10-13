import { apiJson } from "../../shared/api/http";
import type { Personalization, PersonalizationResponse } from "./types";

export const personalizationApi = {
  get: () => apiJson<PersonalizationResponse>("/me/personalization"),
  save: (profile: Personalization) => apiJson<PersonalizationResponse>("/me/personalization", { method: "PUT", body: profile }),
};
