import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/s/": "http://localhost:8080",
      "/swagger-ui": "http://localhost:8080",
      "/v3/api-docs": "http://localhost:8080",
    },
  },
  resolve: { alias: { "@": path.resolve(__dirname, "./src") } },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    restoreMocks: true,
    fileParallelism: false,
    maxWorkers: 1,
    testTimeout: 15_000,
  },
});
