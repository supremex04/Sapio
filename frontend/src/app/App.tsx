import { MotionConfig } from "motion/react";
import { lazy } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router";
import { AuthProvider } from "../features/auth/AuthProvider";
import { LoginPage } from "../features/auth/LoginPage";
import { RegisterPage } from "../features/auth/RegisterPage";
import { RedirectIfAuthenticated, RequireAuth } from "../features/auth/RequireAuth";
import { DashboardPage } from "../features/progress/DashboardPage";
import { WelcomePage } from "../features/progress/WelcomePage";
import { ThemeProvider } from "../features/theme/ThemeProvider";
import { ToastProvider } from "../shared/ui/Toast";
import { AppLayout } from "./AppLayout";

const LibraryPage = lazy(() => import("../features/documents/LibraryPage").then((m) => ({ default: m.LibraryPage })));
const AssessmentPage = lazy(() => import("../features/study/assessment/AssessmentPage").then((m) => ({ default: m.AssessmentPage })));
const TestSetupPage = lazy(() => import("../features/study/assessment/TestSetupPage").then((m) => ({ default: m.TestSetupPage })));
const StudyHubPage = lazy(() => import("../features/study/hub/StudyHubPage").then((m) => ({ default: m.StudyHubPage })));
const LessonPage = lazy(() => import("../features/study/learn/LessonPage").then((m) => ({ default: m.LessonPage })));
const StyleguidePage = lazy(() => import("./styleguide/StyleguidePage").then((m) => ({ default: m.StyleguidePage })));
const ExplorePage = lazy(() => import("../features/sharing/ExplorePage").then((m) => ({ default: m.ExplorePage })));
const SharedItemPage = lazy(() => import("../features/sharing/SharedItemPage").then((m) => ({ default: m.SharedItemPage })));
const PersonalizePage = lazy(() => import("../features/personalization/PersonalizePage").then((m) => ({ default: m.PersonalizePage })));
// Classroom (teachers; students see notices and the calendar)
const StudentsPage = lazy(() => import("../features/classroom/StudentsPage").then((m) => ({ default: m.StudentsPage })));
const StudentProfilePage = lazy(() => import("../features/classroom/StudentProfilePage").then((m) => ({ default: m.StudentProfilePage })));
const StudentReportPage = lazy(() => import("../features/classroom/StudentProfilePage").then((m) => ({ default: m.StudentReportPage })));
const ClassTestPage = lazy(() => import("../features/classroom/ClassTestPage").then((m) => ({ default: m.ClassTestPage })));
const NoticesPage = lazy(() => import("../features/classroom/NoticesPage").then((m) => ({ default: m.NoticesPage })));
const CalendarPage = lazy(() => import("../features/classroom/CalendarPage").then((m) => ({ default: m.CalendarPage })));
const RecallPage = lazy(() => import("../features/study/recall/RecallPage").then((m) => ({ default: m.RecallPage })));

/** Route table. New features add their routes inside the authenticated layout. */
export function App() {
  return (
    <ThemeProvider>
      <MotionConfig reducedMotion="user">
        <ToastProvider>
          <BrowserRouter>
            <AuthProvider>
              <Routes>
                <Route path="/login" element={<RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated>} />
                <Route path="/register" element={<RedirectIfAuthenticated><RegisterPage /></RedirectIfAuthenticated>} />
                <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
                  <Route index element={<DashboardPage />} />
                  <Route path="welcome" element={<WelcomePage />} />
                  <Route path="library" element={<LibraryPage />} />
                  <Route path="explore" element={<ExplorePage />} />
                  <Route path="explore/:shareId" element={<SharedItemPage />} />
                  <Route path="recall" element={<RecallPage />} />
                  <Route path="personalize" element={<PersonalizePage />} />
                  <Route path="review" element={<RedirectToRecall />} />
                  <Route path="documents/:id" element={<StudyHubPage />} />
                  <Route path="documents/:id/learn" element={<Navigate to=".." relative="path" replace />} />
                  <Route path="documents/:id/learn/:topicId" element={<LessonPage />} />
                  <Route path="documents/:id/test" element={<TestSetupPage />} />
                  <Route path="assessments/:id" element={<AssessmentPage />} />
                  <Route path="students" element={<StudentsPage />} />
                  <Route path="students/:studentId" element={<StudentProfilePage />} />
                  <Route path="students/:studentId/tests/:assessmentId" element={<StudentReportPage />} />
                  <Route path="class-tests/:id" element={<ClassTestPage />} />
                  <Route path="notices" element={<NoticesPage />} />
                  <Route path="calendar" element={<CalendarPage />} />
                  <Route path="styleguide" element={<StyleguidePage />} />
                </Route>
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </AuthProvider>
          </BrowserRouter>
        </ToastProvider>
      </MotionConfig>
    </ThemeProvider>
  );
}

/** "Review" was renamed "Recall": old links (and bookmarks) keep working, query string included. */
function RedirectToRecall() {
  const { search } = useLocation();
  return <Navigate to={`/recall${search}`} replace />;
}
