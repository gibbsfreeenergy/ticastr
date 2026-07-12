<template>
  <div>
    <!-- banner -->
    <div class="banner" :style="cover">
      <h1 class="banner-title">归档</h1>
    </div>
    <!-- 归档列表 -->
    <v-card class="blog-container">
      <section class="archive-timeline" aria-label="文章归档">
        <h2 class="archive-timeline__title">目前共计{{ count }}篇文章，继续加油</h2>
        <article class="archive-timeline__item" v-for="item of archiveList" :key="item.id">
          <!-- 日期 -->
          <span class="time">{{ date(item.createTime) }}</span>
          <!-- 文章标题 -->
          <router-link
            :to="'/articles/' + item.id"
            style="color:#666;text-decoration: none"
          >
            {{ item.articleTitle }}
          </router-link>
        </article>
      </section>
      <!-- 分页按钮 -->
      <v-pagination
        color="#00C4B6"
        v-model="current"
        :length="Math.ceil(count / 10)"
        total-visible="7"
      />
    </v-card>
  </div>
</template>

<script>
export default {
  created() {
    this.listArchives();
  },
  data: function() {
    return {
      current: 1,
      count: 0,
      archiveList: []
    };
  },
  methods: {
    listArchives() {
      this.axios
        .get("/api/articles/archives", {
          params: { current: this.current }
        })
        .then(({ data }) => {
          this.archiveList = data.data.recordList;
          this.count = data.data.count;
        });
    }
  },
  computed: {
    cover() {
      var cover = "";
      this.$store.state.blogInfo.pageList.forEach(item => {
        if (item.pageLabel == "archive") {
          cover = item.pageCover;
        }
      });
      return "background: url(" + cover + ") center center / cover no-repeat";
    }
  },
  watch: {
    current(value) {
      this.axios
        .get("/api/articles/archives", {
          params: { current: value }
        })
        .then(({ data }) => {
          this.archiveList = data.data.recordList;
          this.count = data.data.count;
        });
    }
  }
};
</script>

<style scoped>
.time {
  font-size: 0.75rem;
  color: #555;
  margin-right: 1rem;
}

.archive-timeline {
  border-left: 2px solid rgb(var(--v-theme-primary));
  margin: 1rem 0 2rem;
  padding-left: 1.5rem;
}

.archive-timeline__title {
  font-size: 1.25rem;
  margin: 0 0 1rem;
}

.archive-timeline__item {
  margin: 0.75rem 0;
  position: relative;
}

.archive-timeline__item::before {
  background: rgb(var(--v-theme-primary));
  border-radius: 50%;
  content: "";
  height: 0.625rem;
  left: -1.875rem;
  position: absolute;
  top: 0.35rem;
  width: 0.625rem;
}
</style>
