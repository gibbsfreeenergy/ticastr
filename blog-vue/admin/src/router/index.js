import { createRouter, createWebHistory } from "vue-router";

const routes = [
  {
    path: "/login",
    name: "login",
    hidden: true,
    component: () => import("../views/login/Login.vue")
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export function resetRouter() {
  router.getRoutes().forEach(route => {
    if (route.name && route.name !== "login") {
      router.removeRoute(route.name);
    }
  });
}

export default router;
