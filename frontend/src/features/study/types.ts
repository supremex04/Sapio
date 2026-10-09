/** Mirrors docs/specs/study.md. */

export type TopicStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
export type TopicDifficulty = "INTRO" | "CORE" | "ADVANCED";
export type QuestionType = "MULTIPLE_CHOICE" | "TRUE_FALSE" | "SHORT_ANSWER";
export type Verdict = "CORRECT" | "PARTIAL" | "INCORRECT";
export type Confidence = "LOW" | "MEDIUM" | "HIGH";
export type SectionKind = "CONCEPT" | "EXAMPLE" | "ANALOGY" | "SUMMARY";
export type Difficulty = "EASY" | "MIXED" | "HARD";
export type AssessmentStatus = "IN_PROGRESS" | "COMPLETED";
export type TopicLevel = "STRONG" | "DEVELOPING" | "WEAK";

export interface Topic {
  id: string;
  position: number;
  title: string;
  summary: string;
  keyConcepts: string[];
  difficulty: TopicDifficulty;
  status: TopicStatus;
  completedAt: string | null;
  mastery: number | null;
  recommended: boolean;
}

export interface Material {
  documentId: string;
  title: string;
  summary: string;
  language: string;
  topics: Topic[];
  stats: {
    topicCount: number;
    completedTopics: number;
    assessmentsCompleted: number;
    bestScore: number | null;
    reviewsDue: number;
  };
}

export interface AnswerResult {
  questionId: string;
  verdict: Verdict;
  score: number;
  selectedOptionIndex: number | null;
  answerText: string | null;
  correctOptionIndex: number | null;
  referenceAnswer: string | null;
  explanation: string;
  feedback: string;
  confidence: Confidence | null;
  nextReviewAt: string | null;
}

export interface AnswerInput {
  optionIndex?: number;
  text?: string;
  confidence?: Confidence;
}

export interface LessonSection {
  index: number;
  kind: SectionKind;
  heading: string;
  body: string;
}

export interface LessonCheck {
  id: string;
  afterSection: number; // -1 = warm-up before the first section
  type: QuestionType;
  prompt: string;
  options: string[];
  lastAnswer: AnswerResult | null;
}

export interface Lesson {
  topicId: string;
  title: string;
  sections: LessonSection[];
  keyTakeaways: string[];
  checks: LessonCheck[];
  status: Exclude<TopicStatus, "NOT_STARTED">;
}

export interface TopicCompletion {
  topicId: string;
  status: "COMPLETED";
  completedAt: string;
}

export interface TutorTurn {
  role: "user" | "tutor";
  text: string;
}

export interface TutorAnswer {
  answer: string;
  grounded: boolean;
}

export interface QuestionView {
  id: string;
  type: QuestionType;
  prompt: string;
  options: string[];
  topicId: string;
  topicTitle: string;
}

export interface AssessmentItem {
  position: number;
  question: QuestionView;
  answer: AnswerResult | null;
}

export interface TopicResult {
  topicId: string;
  title: string;
  questionCount: number;
  score: number;
  level: TopicLevel;
}

export interface Calibration {
  confidentCorrect: number;
  confidentIncorrect: number;
  unsureCorrect: number;
  unsureIncorrect: number;
}

export interface AssessmentReport {
  score: number;
  correctCount: number;
  partialCount: number;
  incorrectCount: number;
  skippedCount: number;
  topics: TopicResult[];
  summary: string;
  strengths: string[];
  weakAreas: { topicId: string | null; title: string; advice: string }[];
  nextSteps: string[];
  calibration: Calibration | null;
}

export interface Assessment {
  id: string;
  documentId: string;
  documentTitle: string;
  status: AssessmentStatus;
  difficulty: Difficulty;
  questionCount: number;
  answeredCount: number;
  score: number | null;
  createdAt: string;
  completedAt: string | null;
  items: AssessmentItem[];
  report: AssessmentReport | null;
}

export interface AssessmentSummary {
  id: string;
  status: AssessmentStatus;
  difficulty: Difficulty;
  questionCount: number;
  answeredCount: number;
  score: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface CreateAssessmentInput {
  questionCount: number;
  difficulty: Difficulty;
  topicIds?: string[];
}

export interface ReviewSummary {
  dueNow: number;
  dueToday: number;
  total: number;
}

export interface ReviewItem {
  question: QuestionView;
  documentId: string;
  documentTitle: string;
  box: number;
  dueAt: string;
}

export type NextStepKind = "REVIEW" | "CONTINUE_TOPIC" | "START_TOPIC" | "TAKE_TEST" | "UPLOAD";

export interface NextStep {
  kind: NextStepKind;
  documentId: string | null;
  topicId: string | null;
  title: string;
  reason: string;
  estimatedMinutes: number | null;
}
