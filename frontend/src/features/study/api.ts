import { apiJson } from "../../shared/api/http";
import type { AnswerInput, AnswerResult, Assessment, AssessmentSummary, CreateAssessmentInput, Lesson, Material, NextStep, ReviewItem, ReviewSummary, SubmittedAnswer, TopicCompletion, TutorAnswer, TutorFocus, TutorTurn } from "./types";

export const studyApi = {
  material: (documentId: string) => apiJson<Material>(`/documents/${documentId}/material`),
  lesson: (topicId: string) => apiJson<Lesson>(`/topics/${topicId}/lesson`),
  completeTopic: (topicId: string) => apiJson<TopicCompletion>(`/topics/${topicId}/complete`, { method: "POST" }),
  ask: (topicId: string, question: string, history: TutorTurn[], focus?: TutorFocus | null) =>
    apiJson<TutorAnswer>(`/topics/${topicId}/ask`, {
      method: "POST",
      body: {
        question,
        history,
        focus: focus ? { sectionIndex: focus.sectionIndex ?? null, questionId: focus.questionId ?? null } : null,
      },
    }),
  answerQuestion: (questionId: string, input: AnswerInput) =>
    apiJson<AnswerResult>(`/questions/${questionId}/answer`, { method: "POST", body: input }),

  createAssessment: (documentId: string, input: CreateAssessmentInput) =>
    apiJson<Assessment>(`/documents/${documentId}/assessments`, { method: "POST", body: input }),
  assessments: (documentId: string) => apiJson<AssessmentSummary[]>(`/documents/${documentId}/assessments`),
  assessment: (id: string) => apiJson<Assessment>(`/assessments/${id}`),
  answerItem: (id: string, position: number, input: AnswerInput) =>
    apiJson<SubmittedAnswer>(`/assessments/${id}/items/${position}/answer`, { method: "POST", body: input }),
  completeAssessment: (id: string) => apiJson<Assessment>(`/assessments/${id}/complete`, { method: "POST" }),

  reviewSummary: () => apiJson<ReviewSummary>("/reviews/summary"),
  dueReviews: (limit = 10, documentId?: string) =>
    apiJson<{ items: ReviewItem[] }>(`/reviews/due?limit=${limit}${documentId ? `&documentId=${encodeURIComponent(documentId)}` : ""}`),
  answerReview: (questionId: string, input: AnswerInput) =>
    apiJson<AnswerResult>(`/reviews/${questionId}/answer`, { method: "POST", body: input }),

  nextStep: () => apiJson<NextStep>("/study/next-step"),
};
