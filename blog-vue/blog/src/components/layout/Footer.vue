<template>
  <footer class="site-footer">
    <div class="footer-wrap page-width">
      <div class="footer-brand">
        <span class="footer-mark">t</span>
        <div>
          <strong>ticastr</strong>
          <span>{{ blogInfo.websiteConfig.websiteIntro || "记录生活，分享技术" }}</span>
        </div>
      </div>
      <div class="footer-copy">
        <span>慢一点，也很好。</span>
        <span>©{{ year(blogInfo.websiteConfig.websiteCreateTime) }} - {{ new Date().getFullYear() }} {{ blogInfo.websiteConfig.websiteAuthor }}</span>
        <a v-if="blogInfo.websiteConfig.websiteRecordNo" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener">
          {{ blogInfo.websiteConfig.websiteRecordNo }}
        </a>
      </div>
    </div>
  </footer>
</template>

<script>
export default {
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    }
  }
};
</script>

<style scoped>
.site-footer {
  position: relative;
  padding: 48px 0 54px;
  border-top: 1px solid var(--line);
  background: var(--paper);
}

.footer-wrap {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
}

.footer-brand,
.footer-copy {
  display: flex;
  align-items: center;
}

.footer-brand {
  gap: 12px;
}

.footer-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--sage);
  border-radius: 50%;
  color: var(--sage-deep);
  font-family: Georgia, "Times New Roman", serif;
}

.footer-brand div {
  display: flex;
  flex-direction: column;
}

.footer-brand strong {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 18px;
  font-weight: 500;
}

.footer-brand span:last-child {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}

.footer-copy {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px 20px;
  color: var(--muted);
  font-size: 12px;
  text-align: right;
}

.footer-copy > span:first-child {
  color: var(--sage-deep);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 15px;
}

.footer-copy a {
  color: var(--muted);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 640px) {
  .site-footer {
    padding: 36px 0 42px;
  }

  .footer-wrap {
    display: grid;
    gap: 24px;
  }

  .footer-copy {
    justify-content: flex-start;
    text-align: left;
  }
}
</style>
