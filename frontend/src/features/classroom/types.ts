/** Classroom API shapes (backend com.sapio.classroom; docs/specs/classroom.md). Scores are 0-1. */
import type { AssessmentStatus, TopicStatus } from "../study/types";

export type MemberStatus = "PENDING" | "ACCEPTED" | "DECLINED";

export interface Member {
  id: string;
  studentId: string | null;
  email: string;
  name: string | null;
  status: MemberStatus;
  invitedAt: string;
  respondedAt: string | null;
}

export type InviteOutcome = "INVITED" | "ALREADY_INVITED" | "ALREADY_A_STUDENT" | "NOT_A_STUDENT_ACCOUNT" | "INVALID_EMAIL" | "YOURSELF";

export interface InviteResult {
  email: string;
  outcome: InviteOutcome;
}

export interface Invitation {
  id: string;
  teacherName: string;
  teacherEmail: string;
  invitedAt: string;
}

export interface Teacher {
  id: string;
  name: string;
  email: string;
}

export type NotificationType = "CLASS_INVITE" | "INVITE_ANSWERED" | "DOCUMENT_SHARED" | "TEST_ASSIGNED" | "NOTICE" | "EVENT";

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string | null;
  link: string | null;
  actorName: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface Inbox {
  unread: number;
  items: AppNotification[];
}

export interface DocumentStats {
  documentId: string;
  sharedWith: number;
  opened: number;
  testsCompleted: number;
  averageScore: number | null;
}

export interface TopicProgress {
  position: number;
  title: string;
  status: TopicStatus;
  mastery: number | null;
}

export interface TestResult {
  assessmentId: string;
  title: string;
  assigned: boolean;
  status: AssessmentStatus;
  score: number | null;
  answered: number;
  questionCount: number;
  createdAt: string;
  completedAt: string | null;
}

export interface DocumentProgress {
  sourceDocumentId: string;
  documentTitle: string;
  studentId: string;
  studentName: string;
  studentEmail: string;
  removedByStudent: boolean;
  sharedAt: string;
  firstOpenedAt: string | null;
  lastOpenedAt: string | null;
  topicCount: number;
  completedTopics: number;
  averageMastery: number | null;
  topics: TopicProgress[];
  tests: TestResult[];
  averageScore: number | null;
  weakTopics: string[];
}

export interface WeakTopic {
  documentTitle: string;
  topic: string;
  score: number | null;
  reason: string;
}

export interface StudentProfile {
  id: string;
  name: string;
  email: string;
  joinedAt: string | null;
  documents: DocumentProgress[];
  needsWork: WeakTopic[];
  averageScore: number | null;
  testsCompleted: number;
}

export interface RecentResult {
  assessmentId: string;
  studentId: string;
  studentName: string;
  documentTitle: string;
  testTitle: string | null;
  score: number;
  completedAt: string;
}

export interface Attention {
  studentId: string;
  name: string;
  reason: string;
  topics: string[];
}

export interface ClassOverview {
  students: number;
  pendingInvites: number;
  documentsShared: number;
  testsSent: number;
  testsCompleted: number;
  averageScore: number | null;
  lowestScore: number | null;
  highestScore: number | null;
  recent: RecentResult[];
  needsAttention: Attention[];
}

export type TestFormat = "OBJECTIVE" | "SUBJECTIVE" | "MIXED";
export type Difficulty = "EASY" | "MIXED" | "HARD";
export type QuestionType = "MULTIPLE_CHOICE" | "TRUE_FALSE" | "SHORT_ANSWER";

export interface ClassTestSummary {
  id: string;
  documentId: string;
  documentTitle: string;
  title: string;
  format: TestFormat;
  difficulty: Difficulty;
  status: "DRAFT" | "SENT";
  questionCount: number;
  dueAt: string | null;
  createdAt: string;
  sentAt: string | null;
  assigned: number;
  completed: number;
  averageScore: number | null;
}

export interface ClassQuestion {
  id: string;
  position: number;
  topicPosition: number;
  topicTitle: string;
  type: QuestionType;
  prompt: string;
  options: string[];
  correctOption: number | null;
  referenceAnswer: string | null;
  explanation: string;
}

export interface QuestionInput {
  topicPosition: number;
  type: QuestionType;
  prompt: string;
  options: string[];
  correctOption: number | null;
  referenceAnswer: string | null;
  explanation: string;
}

export interface ClassTest {
  summary: ClassTestSummary;
  questions: ClassQuestion[];
  topics: { position: number; title: string }[];
}

export type ResultStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "WITHDRAWN";

export interface StudentResult {
  studentId: string;
  name: string;
  email: string;
  assessmentId: string | null;
  status: ResultStatus;
  answered: number;
  questionCount: number;
  score: number | null;
  completedAt: string | null;
}

export interface TestResults {
  summary: ClassTestSummary;
  students: StudentResult[];
  averageScore: number | null;
  lowestScore: number | null;
  highestScore: number | null;
  topics: { title: string; averageScore: number; students: number }[];
}

export interface Notice {
  id: string;
  teacherName: string;
  title: string;
  body: string;
  createdAt: string;
}

export interface CalendarItem {
  id: string;
  kind: "EVENT" | "TEST_DUE";
  title: string;
  description: string | null;
  startsAt: string;
  endsAt: string | null;
  allDay: boolean;
  teacherName: string;
  link: string | null;
}

export interface EventInput {
  title: string;
  description: string | null;
  startsAt: string;
  endsAt: string | null;
  allDay: boolean;
}
