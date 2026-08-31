<template>
  <header class="chat-header">
    <div class="chat-logo" aria-hidden="true">
      <v-icon color="#fff" size="20">$mdi-chat</v-icon>
    </div>
    <div class="chat-header-copy">
      <strong>聊天室</strong>
      <span>{{ statusLabel }} · 当前{{ onlineCount }}人在线</span>
    </div>
    <button type="button" class="close" aria-label="关闭聊天室" @click="$emit('close')">
      <v-icon>$mdi-close</v-icon>
    </button>
  </header>
</template>

<script>
export default {
  name: "ChatHeader",
  emits: ["close"],
  props: {
    onlineCount: { type: Number, default: 0 },
    status: { type: String, default: "offline" }
  },
  computed: {
    statusLabel() {
      return {
        online: "在线",
        connecting: "连接中",
        reconnecting: "重连中",
        offline: "离线"
      }[this.status] || "离线";
    }
  }
};
</script>

<style scoped>
.chat-header {
  align-items: center;
  background: #fff;
  border-radius: 1rem 1rem 0 0;
  box-shadow: 0 10px 15px -16px rgba(50, 50, 93, 0.08), 0 4px 6px -8px rgba(50, 50, 93, 0.04);
  display: flex;
  min-height: 80px;
  padding: 20px 24px;
}

.chat-logo {
  align-items: center;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 10px;
  display: flex;
  height: 32px;
  justify-content: center;
  width: 32px;
}

.chat-header-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-left: 12px;
}

.chat-header-copy span {
  color: #8993a5;
  font-size: 12px;
}

.close {
  align-items: center;
  background: transparent;
  border: 0;
  color: #536174;
  cursor: pointer;
  display: inline-flex;
  height: 32px;
  justify-content: center;
  margin-left: auto;
  padding: 0;
}
</style>
