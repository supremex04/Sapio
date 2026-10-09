import type { ReactNode } from "react";

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="auth-screen">
      <div className="auth-card">
        <div className="brand brand-lg">Ved</div>
        <h1>{title}</h1>
        <p className="muted">{subtitle}</p>
        {children}
      </div>
    </main>
  );
}
