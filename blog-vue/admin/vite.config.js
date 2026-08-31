import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { createDevProxy } from "../shared/vite/createDevProxy";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": "/src"
    }
  },
  server: {
    proxy: createDevProxy()
  },
  build: {
    sourcemap: false,
    rolldownOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("node_modules/vue/") || id.includes("node_modules/@vue/") || id.includes("node_modules/vuex/")) {
            return "vue-vendor";
          }
        }
      }
    }
  }
});
