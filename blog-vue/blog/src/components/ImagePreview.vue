<template>
  <v-dialog v-model="visible" max-width="1000">
    <v-carousel v-model="currentIndex" hide-delimiters show-arrows="hover">
      <v-carousel-item v-for="image in images" :key="image" :src="image" cover />
    </v-carousel>
  </v-dialog>
</template>

<script>
export default {
  data() {
    return {
      currentIndex: 0,
      images: [],
      visible: false
    };
  },
  mounted() {
    window.addEventListener("image-preview", this.openPreview);
  },
  beforeUnmount() {
    window.removeEventListener("image-preview", this.openPreview);
  },
  methods: {
    openPreview({ detail }) {
      this.images = detail.images || [];
      this.currentIndex = detail.index || 0;
      this.visible = this.images.length > 0;
    }
  }
};
</script>
