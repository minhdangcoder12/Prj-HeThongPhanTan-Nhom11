import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Rule R7: the client only talks to the gateway; Vite proxies /api to it.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": { target: process.env.GATEWAY_URL ?? "http://localhost:8080", changeOrigin: true } },
  },
});
