import "@fontsource-variable/inter";
import "@fontsource-variable/fraunces/full.css";
// Global styles first, so feature stylesheets (imported by components) can override them.
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/components.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
