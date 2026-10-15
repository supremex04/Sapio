import type { ReactNode } from "react";
import { Link } from "react-router";
import { Wordmark } from "../../app/Logo";
import { Sage, type SageMood } from "../../shared/illustrations/Sage";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SpeechBubble } from "../../shared/ui/SpeechBubble";
import "./auth.css";

interface Props {
  title: string;
  subtitle: string;
  mood: SageMood;
  greeting: ReactNode;
  children: ReactNode;
}

/** The product's promise, shown beside every sign-in and sign-up form. */
export const SLOGAN = { lead: "While the world races to create smarter AI, we intend to make", emphasis: "smarter humans" };

/**
 * Auth screen in two halves: the slogan on the left, Sapi's hello and one clean form card on the right.
 * On narrow screens the slogan sits above the form.
 */
export function AuthLayout({ title, subtitle, mood, greeting, children }: Props) {
  return (
    <main className="auth-screen">
      <section className="auth-pitch" aria-label="Why Sapio">
        <Link to="/login" aria-label="Sapio home" className="auth-brand">
          <Wordmark />
        </Link>
        <p className="auth-slogan">
          {SLOGAN.lead} <em>{SLOGAN.emphasis}</em>.
        </p>
        <p className="auth-origin xs muted">Sapio, from Latin sapiō: "I understand".</p>
      </section>
      <div className="auth-entry">
        <PageTransition>
          <div className="auth-column">
            <div className="auth-hello">
              <Sage mood={mood} size={104} />
              <SpeechBubble>{greeting}</SpeechBubble>
            </div>
            <div className="auth-card">
              <div className="auth-heading">
                <h1>{title}</h1>
                <p className="muted">{subtitle}</p>
              </div>
              {children}
            </div>
          </div>
        </PageTransition>
      </div>
    </main>
  );
}
