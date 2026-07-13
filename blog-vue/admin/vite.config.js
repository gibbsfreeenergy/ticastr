import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": "/src"
    }
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, ""),
        ws: true
      }
    }
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
