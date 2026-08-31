import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { createDevProxy } from "../shared/vite/createDevProxy";

export default defineConfig({
  plugins: [vue()],
  define: {
    global: "globalThis"
  },
  resolve: {
    alias: { "@": "/src" },
    extensions: [".mjs", ".js", ".mts", ".ts", ".jsx", ".tsx", ".json", ".vue"]
  },
  server: {
    port: 8080,
    proxy: createDevProxy()
  },
  build: {
    sourcemap: false,
    rolldownOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("node_modules/vue") || id.includes("node_modules/@vue") || id.includes("node_modules/vuex") || id.includes("node_modules/vuetify")) {
            return "vue-vendor";
          }
          if (id.includes("node_modules/markdown-it") || id.includes("node_modules/highlight.js") || id.includes("node_modules/tocbot")) {
            return "content-vendor";
          }
        }
      }
    }
  }
});
