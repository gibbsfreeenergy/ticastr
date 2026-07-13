import vue from "eslint-plugin-vue";
import globals from "globals";

export default [
  { ignores: ["dist/**", "node_modules/**", "src/assets/js/china.js"] },
  ...vue.configs["flat/essential"],
  {
    files: ["**/*.{js,vue}"],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node, TencentCaptcha: "readonly" }
    },
    rules: {
      "vue/multi-word-component-names": "off",
      "vue/no-v-html": "error",
      "no-console": ["warn", { allow: ["warn", "error"] }]
    }
  }
];
