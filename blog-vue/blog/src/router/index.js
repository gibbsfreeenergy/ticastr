import { createRouter, createWebHistory } from "vue-router";

const routes = [
  { path: "/", component: () => import("../views/home/Home.vue") },
  { path: "/articles/:articleId", component: () => import("../views/article/Article.vue") },
  { path: "/archives", component: () => import("../views/archive/Archive.vue"), meta: { title: "归档" } },
  { path: "/about", component: () => import("../views/about/About.vue"), meta: { title: "关于我" } }
];

export default createRouter({ history: createWebHistory(), routes });
