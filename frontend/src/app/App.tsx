import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { AuthProvider } from "../features/auth/AuthProvider";
import { LoginPage } from "../features/auth/LoginPage";
import { RegisterPage } from "../features/auth/RegisterPage";
import { RedirectIfAuthenticated, RequireAuth } from "../features/auth/RequireAuth";
import { DocumentDetailPage } from "../features/documents/DocumentDetailPage";
import { DocumentsPage } from "../features/documents/DocumentsPage";
import { AppLayout } from "./AppLayout";

/** Route table. New features add their routes here, inside the authenticated layout. */
export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated>} />
          <Route path="/register" element={<RedirectIfAuthenticated><RegisterPage /></RedirectIfAuthenticated>} />
          <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
            <Route index element={<DocumentsPage />} />
            <Route path="documents/:id" element={<DocumentDetailPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
