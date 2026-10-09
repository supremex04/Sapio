import type { ReactNode } from "react";
import { Link } from "react-router";
import { Wordmark } from "../../app/Logo";
import { Dia, type DiaMood } from "../../shared/illustrations/Dia";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SpeechBubble } from "../../shared/ui/SpeechBubble";
import "./auth.css";

interface Props {
  title: string;
  subtitle: string;
  mood: DiaMood;
  greeting: ReactNode;
  children: ReactNode;
}

/** Centered, airy auth screen: Dia says hello, then one clean form card. */
export function AuthLayout({ title, subtitle, mood, greeting, children }: Props) {
  return (
    <main className="auth-screen">
      <header className="auth-top">
        <Link to="/login" aria-label="Ved home" className="auth-brand">
          <Wordmark />
        </Link>
      </header>
      <PageTransition>
        <div className="auth-column">
          <div className="auth-hello">
            <Dia mood={mood} size={104} />
            <SpeechBubble>{greeting}</SpeechBubble>
          </div>
          <div className="auth-card">
            <div className="auth-heading">
              <h1>{title}</h1>
              <p className="muted">{subtitle}</p>
            </div>
            {children}
          </div>
          <p className="auth-foot xs muted center">Ved, from Sanskrit veda: "knowledge".</p>
        </div>
      </PageTransition>
    </main>
  );
}
