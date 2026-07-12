<template>
  <el-card class="main-card">
    <div class="title">{{ this.$route.name }}</div>
    <md-editor
      ref="md"
      @onUploadImg="uploadImg"
      v-model="aboutContent"
      style="height:calc(100vh - 250px);margin-top:2.25rem"
    />
    <el-button
      type="danger"
      size="medium"
      class="edit-btn"
      @click="updateAbout"
    >
      修改
    </el-button>
  </el-card>
</template>

<script>
import * as imageConversion from "image-conversion";
export default {
  created() {
    this.getAbout();
  },
  data: function() {
    return {
      aboutContent: ""
    };
  },
  methods: {
    getAbout() {
      this.axios.get("/api/about").then(({ data }) => {
        this.aboutContent = data.data;
      });
    },
    async uploadImg(files, callback) {
      const urls = await Promise.all(Array.from(files).map(file => this.uploadImage(file)));
      callback(urls);
    },
    async uploadImage(file) {
      const formdata = new FormData();
      let uploadFile = file;
      if (file.size / 1024 >= this.config.UPLOAD_SIZE) {
        const compressedFile = await imageConversion.compressAccurately(file, this.config.UPLOAD_SIZE);
        uploadFile = new window.File([compressedFile], file.name, { type: file.type });
      }
      formdata.append("file", uploadFile);
      const { data } = await this.axios.post("/api/admin/articles/images", formdata);
      return data.data;
    },    updateAbout() {
      this.axios
        .put("/api/admin/about", {
          aboutContent: this.aboutContent
        })
        .then(({ data }) => {
          if (data.flag) {
            this.$notify.success({
              title: "成功",
              message: data.message
            });
          } else {
            this.$notify.error({
              title: "失败",
              message: data.message
            });
          }
        });
    }
  }
};
</script>

<style scoped>
.edit-btn {
  float: right;
  margin: 1rem 0;
}
</style>
