import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The proxy lets the browser talk to both services from one origin,
// so you don't need to add CORS config to content-service / streaming-service.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/content": {
        target: "http://localhost:8081", // content-service
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/content/, ""),
      },
      "/stream": {
        target: "http://localhost:8084", // streaming-service
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/stream/, ""),
      },
    },
  },
});
