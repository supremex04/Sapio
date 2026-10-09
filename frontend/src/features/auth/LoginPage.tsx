import { ArrowRight } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { FormField } from "../../shared/ui/FormField";
import { isOnboarded } from "../progress/onboarding";
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
      const user = await login({ email: String(form.get("email")), password: String(form.get("password")) });
      const from = (location.state as { from?: string } | null)?.from;
      navigate(isOnboarded(user.id) ? (from ?? "/") : "/welcome", { replace: true });
    } catch (e) {
      setError(errorMessage(e));
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Pick up right where you left off." mood="happy" greeting="Hi again! Ready for a few minutes of learning?">
      <form onSubmit={onSubmit} className="stack" noValidate>
        {error && <Alert>{error}</Alert>}
        <FormField label="Email" name="email" type="email" autoComplete="email" required autoFocus />
        <FormField label="Password" name="password" type="password" autoComplete="current-password" required />
        <Button variant="primary" size="lg" type="submit" loading={submitting} iconRight={<ArrowRight size={20} />} block>
          Sign in
        </Button>
      </form>
      <p className="muted small center">
        New to Ved? <Link to="/register">Create a free account</Link>
      </p>
    </AuthLayout>
  );
}
