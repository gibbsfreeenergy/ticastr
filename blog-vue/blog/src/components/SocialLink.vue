<template>
  <a
    class="social-link"
    :class="`social-link-${type}`"
    :href="href"
    :aria-label="label"
    target="_blank"
    rel="noopener"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        v-if="type === 'github'"
        class="social-github-mark"
        d="M12 2A10 10 0 0 0 2 12c0 4.42 2.87 8.17 6.84 9.5.5.08.66-.22.66-.5v-1.72c-2.78.6-3.37-1.34-3.37-1.34-.46-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.9 1.53 2.35 1.09 2.92.83.09-.65.35-1.09.64-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.98 1.03-2.67-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.02A9.58 9.58 0 0 1 12 6.8c.85 0 1.71.12 2.51.34 1.91-1.29 2.75-1.02 2.75-1.02.55 1.38.2 2.4.1 2.65.64.69 1.03 1.58 1.03 2.67 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.85V21c0 .28.18.59.67.5A10 10 0 0 0 12 2Z"
      />
      <g v-else-if="type === 'qq'">
        <path d="M12 5.25c-3.25 0-5.85 2.13-5.85 4.76 0 1.6.91 3.03 2.32 3.9-.08.87-.48 1.75-1.35 2.65 1.18-.1 2.37-.51 3.26-1.24.51.13 1.06.2 1.62.2 3.25 0 5.85-2.13 5.85-4.76S15.25 5.25 12 5.25Z" />
        <path d="M9.5 10.2h.01M12 10.2h.01M14.5 10.2h.01" />
      </g>
      <path
        v-else
        d="M17.55 7.5h-4.8a4.5 4.5 0 1 0 0 9h2.62a2.93 2.93 0 0 0 2.93-2.93v-.05h-4.2"
      />
    </svg>
  </a>
</template>

<script>
export default {
  name: "SocialLink",
  props: {
    href: { type: String, default: "#" },
    label: { type: String, required: true },
    type: { type: String, required: true }
  }
};
</script>

<style scoped>
.social-link {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 50%;
  color: currentColor;
  background: rgba(255, 255, 255, 0.08);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  transition: color 180ms ease, background 180ms ease, border-color 180ms ease, transform 180ms ease;
}

.social-link:hover {
  border-color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  transform: translateY(-2px);
}

.social-link svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.social-link-github svg {
  fill: currentColor;
  stroke: none;
}

.social-link-gitee svg {
  stroke-width: 1.8;
}
</style>
