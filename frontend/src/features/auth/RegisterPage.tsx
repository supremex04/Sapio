import { ArrowRight, GraduationCap, Presentation } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { ApiError, errorMessage } from "../../shared/api/errors";
import { browserTimeZone } from "../../shared/lib/timezone";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { cx } from "../../shared/ui/cx";
import { FormField } from "../../shared/ui/FormField";
import { useAuth } from "./AuthContext";
import { AuthLayout } from "./AuthLayout";
import type { RegisterInput } from "./types";

const ROLES = [
  { value: "STUDENT", label: "I'm learning", icon: GraduationCap },
  { value: "TEACHER", label: "I'm teaching", icon: Presentation },
] as const;

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [role, setRole] = useState<RegisterInput["role"]>("STUDENT");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({
        fullName: String(form.get("fullName")),
        email: String(form.get("email")),
        password: String(form.get("password")),
        role,
        timeZone: browserTimeZone(),
      });
      navigate("/welcome", { replace: true });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) setFieldErrors(e.fieldErrors);
      else setError(errorMessage(e));
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Two minutes from now you'll be learning from your own material.">
      <form onSubmit={onSubmit} className="stack" noValidate>
        {error && <Alert>{error}</Alert>}
        <fieldset className="role-picker">
          <legend>How will you use Ved?</legend>
          {ROLES.map(({ value, label, icon: Icon }) => (
            <label key={value} className={cx("role-option", role === value && "selected")}>
              <input type="radio" name="role" value={value} checked={role === value} onChange={() => setRole(value)} />
              <Icon size={18} aria-hidden />
              {label}
            </label>
          ))}
        </fieldset>
        <FormField label="Full name" name="fullName" autoComplete="name" required error={fieldErrors.fullName} />
        <FormField label="Email" name="email" type="email" autoComplete="email" required error={fieldErrors.email} />
        <FormField
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          minLength={8}
          required
          placeholder="At least 8 characters"
          error={fieldErrors.password}
        />
        <Button variant="primary" size="lg" type="submit" loading={submitting} iconRight={<ArrowRight size={18} />} block>
          Create account
        </Button>
      </form>
      <p className="muted small center">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  );
}
