<template>
  <div>
    <!-- banner -->
    <div class="banner" :style="cover">
      <h1 class="banner-title">{{ photoAlbumName }}</h1>
    </div>
    <!-- 相册列表 -->
    <v-card class="blog-container">
      <div class="photo-wrap">
        <img
          v-for="(item, index) of photoList"
          class="photo"
          :key="index"
          :src="item"
          @click="preview(index)"
        />
      </div>
      <div class="photo-list-status" role="status">
        <span v-if="loading">加载中...</span>
        <button v-else-if="loadError || hasMore" type="button" @click="loadMore">
          {{ loadError ? "重试" : "加载更多" }}
        </button>
        <span v-else>{{ photoList.length ? "没有更多照片" : "暂无照片" }}</span>
      </div>
    </v-card>
  </div>
</template>

<script>
export default {
  created() {
    this.loadMore();
  },
  data: function() {
    return {
      photoAlbumName: "",
      photoAlbumCover: "",
      photoList: [],
      current: 1,
      size: 10,
      loading: false,
      hasMore: true,
      loadError: false
    };
  },
  methods: {
    preview(index) {
      this.$imagePreview({
        images: this.photoList,
        index: index
      });
    },
    loadMore() {
      if (this.loading || (!this.hasMore && !this.loadError)) return;
      this.loading = true;
      this.loadError = false;
      this.$api.album
        .photos(this.$route.params.albumId, {
          params: {
            current: this.current,
            size: this.size
          }
        })
        .then(data => {
          const result = data.data || {};
          const photoList = result.photoList || [];
          this.photoAlbumCover = result.photoAlbumCover || "";
          this.photoAlbumName = result.photoAlbumName || "相册照片";
          this.current++;
          this.photoList.push(...photoList);
          this.hasMore = photoList.length >= this.size;
        })
        .catch(() => {
          this.loadError = true;
          this.hasMore = true;
        })
        .finally(() => {
          this.loading = false;
        });
    }
  },
  computed: {
    cover() {
      return (
        "background: url(" +
        this.photoAlbumCover +
        ") center center / cover no-repeat"
      );
    }
  }
};
</script>

<style scoped>
.photo-wrap {
  display: flex;
  flex-wrap: wrap;
}
.photo {
  margin: 3px;
  cursor: pointer;
  flex-grow: 1;
  object-fit: cover;
  height: 200px;
}
.photo-wrap::after {
  content: "";
  display: block;
  flex-grow: 9999;
}
.photo-list-status {
  padding: 1.25rem;
  color: #8a8a8a;
  text-align: center;
}
.photo-list-status button {
  border: 0;
  border-radius: 999px;
  padding: 0.5rem 1.25rem;
  color: #fff;
  background: linear-gradient(135deg, #49b1f5, #8e8cd8);
  cursor: pointer;
}
@media (max-width: 759px) {
  .photo {
    width: 100%;
  }
}
</style>
