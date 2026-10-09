import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the UI calls /api on its own origin and Vite forwards it to the backend.
// That keeps the refresh-token cookie first-party (SameSite=Strict) without CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: process.env.VITE_BACKEND_URL ?? "http://localhost:8080", changeOrigin: false },
    },
  },
});
