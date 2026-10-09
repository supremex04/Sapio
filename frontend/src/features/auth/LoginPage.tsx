import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Alert } from "../../shared/ui/Alert";
import { FormField } from "../../shared/ui/FormField";
import { useAuth } from "./AuthContext";
import { AuthLayout } from "./AuthLayout";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      await login({ email: String(form.get("email")), password: String(form.get("password")) });
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? "/", { replace: true });
    } catch (e) {
      setError(errorMessage(e));
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Sign in" subtitle="Welcome back to Ved.">
      <form onSubmit={onSubmit} className="stack" noValidate>
        {error && <Alert>{error}</Alert>}
        <FormField label="Email" name="email" type="email" autoComplete="email" required autoFocus />
        <FormField label="Password" name="password" type="password" autoComplete="current-password" required />
        <button className="btn btn-primary" type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="muted center">
        New here? <Link to="/register">Create an account</Link>
      </p>
    </AuthLayout>
  );
}
