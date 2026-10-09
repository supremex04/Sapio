import { Brain, Repeat, Sparkles } from "lucide-react";
import type { ReactNode } from "react";
import { Logo } from "../../app/Logo";
import { PageTransition } from "../../shared/ui/PageTransition";
import "./auth.css";

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="auth-screen">
      <aside className="auth-showcase" aria-hidden>
        <div className="auth-orbit" />
        <span className="brand">
          <Logo />
          <span>Ved</span>
        </span>
        <div>
          <h2>Turn any document into a course that teaches you.</h2>
          <p>Upload a syllabus, a textbook or your lecture notes. Ved builds lessons, checks your understanding and helps you remember it.</p>
          <ul className="auth-features">
            <li>
              <Sparkles size={18} /> Lessons built only from your material
            </li>
            <li>
              <Brain size={18} /> Questions that show what you really know
            </li>
            <li>
              <Repeat size={18} /> Spaced reviews so it sticks
            </li>
          </ul>
        </div>
        <span className="auth-foot">Ved: from Sanskrit veda, "knowledge".</span>
      </aside>
      <section className="auth-panel">
        <PageTransition>
          <div className="auth-card">
            <span className="brand brand-mobile">
              <Logo />
              <span>Ved</span>
            </span>
            <div className="stack-sm">
              <h1>{title}</h1>
              <p className="muted">{subtitle}</p>
            </div>
            {children}
          </div>
        </PageTransition>
      </section>
    </main>
  );
}
