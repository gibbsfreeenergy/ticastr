<template>
  <div v-show="chooseEmoji" class="emoji-wrapper">
    <span
      class="emoji-item"
      v-for="(value, index) in emojiList"
      :key="index"
      @click="addEmoji(value)"
    >
      <!-- <v-img
        :lazy-src="value"
        :src="value"
        :title="key"
        class="emoji"
        width="24"
        height="24"
      /> -->
      <p class="emoji" width="16" height="16">{{ value }}</p>
    </span>
  </div>
</template>

<script>
import EmojiList from "../assets/js/emoji";
export default {
  props: {
    chooseEmoji: {
      type: Boolean
    }
  },
  data: function() {
    return {
      emojiList: EmojiList
    };
  },
  methods: {
    addEmoji(value) {
      this.$emit("addEmoji", value);
    }
  }
};
</script>

<style scoped>
.emoji {
  user-select: none;
  margin: 0.1rem;
  display: inline-block;
  vertical-align: middle;
}
.emoji-item {
  cursor: pointer;
  display: inline-block;
  height: 25px;
}
.emoji-item:hover {
  transition: all 0.2s;
  border-radius: 0.25rem;
  background: #dddddd;
}
.emoji-wrapper {
  max-height: 150px;
  overflow-y: auto;
}
</style>
