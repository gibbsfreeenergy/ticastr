<template>
  <svg
    class="app-icon"
    :class="`app-icon--${name}`"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <path
      v-for="(path, index) in iconPaths"
      :key="index"
      :d="path"
      stroke="currentColor"
      :stroke-width="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<script>
export default {
  name: "AppIcon",
  props: {
    name: {
      type: String,
      default: "grid"
    },
    size: {
      type: [Number, String],
      default: 18
    },
    strokeWidth: {
      type: [Number, String],
      default: 1.8
    }
  },
  computed: {
    iconPaths() {
      const icons = {
        home: ["M3 10.5 12 3l9 7.5", "M5.5 9.5V21h13V9.5", "M9.5 21v-6h5v6"],
        pen: ["m4 20 4.2-1 9.9-9.9a2.1 2.1 0 0 0-3-3L5.2 16 4 20Z", "m13.8 7.2 3 3"],
        file: ["M6 3.5h8l4 4V20.5H6z", "M14 3.5v4h4", "M9 12h6", "M9 16h6"],
        folder: ["M3.5 6.5h6l1.8 2h9.2v10.8H3.5z", "M3.5 8.5h17"],
        globe: ["M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z", "M3 12h18", "M12 3c2.2 2.5 3.3 5.5 3.3 9S14.2 18.5 12 21", "M12 3C9.8 5.5 8.7 8.5 8.7 12s1.1 6.5 3.3 9"],
        page: ["M6 3.5h8l4 4v13H6z", "M14 3.5v4h4", "M9 12h6", "M9 16h4"],
        info: ["M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z", "M12 10v6", "M12 7h.01"],
        settings: ["M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z", "M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-1.8 1.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-2.5V20a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1-1.8-1.8.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H6v-2.5h.2a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1 1.8-1.8.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6V5h2.5v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1 1.8 1.8-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v2.5h-.2a1.7 1.7 0 0 0-1.6 1Z"],
        search: ["m20 20-4.4-4.4", "M10.8 17a6.2 6.2 0 1 0 0-12.4 6.2 6.2 0 0 0 0 12.4Z"],
        fullscreen: ["M8 3H3v5", "M16 3h5v5", "M21 16v5h-5", "M3 16v5h5"],
        menu: ["M4 6h16", "M4 12h16", "M4 18h16"],
        chevronLeft: ["m14.5 5-7 7 7 7"],
        chevronRight: ["m9.5 5 7 7-7 7"],
        chevronDown: ["m6 9 6 6 6-6"],
        close: ["m6 6 12 12", "m18 6-12 12"],
        plus: ["M12 5v14", "M5 12h14"],
        filter: ["M4 5h16l-6.5 7.5V18l-3 1v-6.5z"],
        eye: ["M3 12s3.2-5 9-5 9 5 9 5-3.2 5-9 5-9-5-9-5Z", "M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z"],
        grid: ["M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z"]
      };
      return icons[this.name] || icons.grid;
    }
  }
};
</script>

<style scoped>
.app-icon {
  display: inline-block;
  flex: 0 0 auto;
  vertical-align: middle;
}
</style>
