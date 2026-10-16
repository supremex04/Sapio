import { apiJson, apiRequest } from "../../shared/api/http";
import type { Assessment } from "../study/types";
import type {
  CalendarItem,
  ClassOverview,
  ClassTest,
  ClassTestSummary,
  Difficulty,
  DocumentProgress,
  DocumentStats,
  EventInput,
  Inbox,
  Invitation,
  InviteResult,
  Member,
  Notice,
  QuestionInput,
  StudentProfile,
  Teacher,
  TestFormat,
  TestResults,
} from "./types";

const C = "/classroom";

export const classroomApi = {
  // notifications (every user)
  inbox: () => apiJson<Inbox>("/notifications"),
  markRead: () => apiRequest("/notifications/read", { method: "POST" }),

  // teacher
  overview: () => apiJson<ClassOverview>(`${C}/overview`),
  roster: () => apiJson<Member[]>(`${C}/students`),
  invite: (emails: string[]) => apiJson<InviteResult[]>(`${C}/students/invite`, { method: "POST", body: { emails } }),
  removeMember: (membershipId: string) => apiRequest(`${C}/students/members/${membershipId}`, { method: "DELETE" }),
  student: (studentId: string) => apiJson<StudentProfile>(`${C}/students/${studentId}`),
  studentAssessment: (assessmentId: string) => apiJson<Assessment>(`${C}/assessments/${assessmentId}`),
  documentStats: () => apiJson<DocumentStats[]>(`${C}/documents/stats`),
  share: (documentId: string, studentIds: string[]) =>
    apiJson<{ shared: number; alreadyHad: number }>(`${C}/documents/${documentId}/share`, { method: "POST", body: { studentIds } }),
  documentStudents: (documentId: string) => apiJson<DocumentProgress[]>(`${C}/documents/${documentId}/students`),
  tests: (documentId?: string) => apiJson<ClassTestSummary[]>(`${C}/tests${documentId ? `?documentId=${documentId}` : ""}`),
  createTest: (documentId: string, input: { title: string; format: TestFormat; difficulty: Difficulty; questionCount: number }) =>
    apiJson<ClassTest>(`${C}/documents/${documentId}/tests`, { method: "POST", body: input }),
  test: (testId: string) => apiJson<ClassTest>(`${C}/tests/${testId}`),
  updateTest: (testId: string, title: string, dueAt: string | null) =>
    apiJson<ClassTest>(`${C}/tests/${testId}`, { method: "PUT", body: { title, dueAt } }),
  deleteTest: (testId: string) => apiRequest(`${C}/tests/${testId}`, { method: "DELETE" }),
  addQuestion: (testId: string, q: QuestionInput) => apiJson<ClassTest>(`${C}/tests/${testId}/questions`, { method: "POST", body: q }),
  updateQuestion: (testId: string, questionId: string, q: QuestionInput) =>
    apiJson<ClassTest>(`${C}/tests/${testId}/questions/${questionId}`, { method: "PUT", body: q }),
  deleteQuestion: (testId: string, questionId: string) =>
    apiJson<ClassTest>(`${C}/tests/${testId}/questions/${questionId}`, { method: "DELETE" }),
  send: (testId: string, studentIds: string[], dueAt: string | null) =>
    apiJson<{ sent: number }>(`${C}/tests/${testId}/send`, { method: "POST", body: { studentIds, dueAt } }),
  results: (testId: string) => apiJson<TestResults>(`${C}/tests/${testId}/results`),
  notices: () => apiJson<Notice[]>(`${C}/notices`),
  postNotice: (title: string, body: string) => apiJson<Notice>(`${C}/notices`, { method: "POST", body: { title, body } }),
  deleteNotice: (id: string) => apiRequest(`${C}/notices/${id}`, { method: "DELETE" }),
  calendar: (from: Date, to: Date) =>
    apiJson<CalendarItem[]>(`${C}/calendar?from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`),
  createEvent: (e: EventInput) => apiJson<CalendarItem>(`${C}/events`, { method: "POST", body: e }),
  updateEvent: (id: string, e: EventInput) => apiJson<CalendarItem>(`${C}/events/${id}`, { method: "PUT", body: e }),
  deleteEvent: (id: string) => apiRequest(`${C}/events/${id}`, { method: "DELETE" }),

  // student
  invitations: () => apiJson<Invitation[]>(`${C}/invitations`),
  accept: (id: string) => apiRequest(`${C}/invitations/${id}/accept`, { method: "POST" }),
  decline: (id: string) => apiRequest(`${C}/invitations/${id}/decline`, { method: "POST" }),
  teachers: () => apiJson<Teacher[]>(`${C}/teachers`),
  leave: (teacherId: string) => apiRequest(`${C}/teachers/${teacherId}`, { method: "DELETE" }),
  opened: (documentId: string) => apiRequest(`${C}/opened/${documentId}`, { method: "POST" }),
};
