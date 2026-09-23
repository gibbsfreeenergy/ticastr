<template>
  <svg class="nav-icon-svg" viewBox="0 0 24 24" aria-hidden="true">
    <template v-if="name === 'search'">
      <circle cx="10.8" cy="10.8" r="6.6" />
      <path d="m16 16 4.2 4.2" />
    </template>
    <path v-else d="M4 7h16M4 12h16M4 17h16" />
  </svg>
</template>

<script>
export default {
  name: "NavIcon",
  props: {
    name: { type: String, default: "search" }
  }
};
</script>

<style scoped>
.nav-icon-svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}
</style>
