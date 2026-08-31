import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { "@": "/src" } },
  test: {
    environment: "jsdom",
    setupFiles: [],
    clearMocks: true,
    exclude: ["**/node_modules/**", "scripts/**/*.test.mjs"]
  }
});
