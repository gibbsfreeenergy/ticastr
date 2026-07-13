import { createRouter, createWebHistory } from "vue-router";

const routes = [
  {
    path: "/login",
    name: "login",
    hidden: true,
    component: () => import("../views/login/Login.vue")
  },
  {
    path: "/:pathMatch(.*)*",
    name: "not-found",
    hidden: true,
    component: () => import("../views/error/NotFound.vue")
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export function resetRouter() {
  router.getRoutes().forEach(route => {
    if (route.name && !["login", "not-found"].includes(route.name)) {
      router.removeRoute(route.name);
    }
  });
}

export default router;
