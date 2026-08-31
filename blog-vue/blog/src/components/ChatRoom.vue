<template>
  <div>
    <section
      v-show="isShow"
      class="chat-container animated bounceInUp"
      role="dialog"
      aria-label="聊天室"
      @click="closeAll"
      @contextmenu.prevent.stop="closeAll"
    >
      <ChatHeader :onlineCount="onlineCount" :status="status" @close="close" />
      <ChatMessageList
        :messages="messages"
        :pendingMessages="pendingMessages"
        :hasMore="hasMore"
        :historyLoading="historyLoading"
        :status="status"
        @close-menu="closeAll"
        @load-history="loadHistory"
        @recall="recall"
        @retry="retryMessage"
      />
      <ChatComposer
        :modelValue="draft"
        :isVoice="isVoice"
        :isEmoji="isEmoji"
        :disabled="voiceSending"
        @update:modelValue="draft = $event"
        @send="sendMessage"
        @toggle-voice="toggleVoice"
        @toggle-emoji="toggleEmoji"
        @voice-recorded="sendVoice"
        @error="showError"
      />
    </section>
    <button type="button" class="chat-btn" aria-label="打开聊天室" @click="open">
      <span v-if="unreadCount > 0" class="unread">{{ unreadCount > 99 ? "99+" : unreadCount }}</span>
      <v-icon color="#fff" size="30" :icon="'$mdi-chat-outline'" />
    </button>
  </div>
</template>

<script>
import ChatComposer from "./chat/ChatComposer";
import ChatHeader from "./chat/ChatHeader";
import ChatMessageList from "./chat/ChatMessageList";
import { createChatState } from "./chat/chatState";

function createClientId() {
  const storageKey = "ticastr.chat.client-id";
  try {
    const existing = window.localStorage.getItem(storageKey);
    if (existing) return existing;
    const generated = window.crypto?.randomUUID?.();
    if (!generated) return null;
    window.localStorage.setItem(storageKey, generated);
    return generated;
  } catch {
    return null;
  }
}

export default {
  name: "ChatRoom",
  components: { ChatComposer, ChatHeader, ChatMessageList },
  data() {
    return {
      clientId: null,
      chat: null,
      isShow: false,
      isEmoji: false,
      isVoice: false,
      draft: "",
      status: "offline",
      onlineCount: 0,
      messages: [],
      pendingMessages: [],
      historyLoading: false,
      hasMore: false,
      unreadCount: 0,
      voiceSending: false
    };
  },
  created() {
    this.clientId = createClientId();
    this.chat = createChatState({
      clientId: this.clientId,
      onChange: state => this.syncState(state),
      onMessage: () => {
        if (!this.isShow) this.unreadCount += 1;
      },
      onProtocolError: () => this.showError("聊天室收到无效消息")
    });
  },
  beforeUnmount() {
    this.chat?.close();
    this.chat = null;
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    websocketUrl() {
      return this.blogInfo?.websiteConfig?.websocketUrl || "";
    }
  },
  methods: {
    syncState(state) {
      this.status = state.status;
      this.onlineCount = state.onlineCount;
      this.messages = state.messages.slice();
      this.pendingMessages = state.pendingMessages.slice();
      this.historyLoading = state.historyLoading;
      this.hasMore = state.hasMore;
    },
    open() {
      this.isShow = !this.isShow;
      if (this.isShow) {
        this.unreadCount = 0;
        this.connect();
      }
    },
    close() {
      this.isShow = false;
      this.isEmoji = false;
    },
    connect() {
      if (!this.chat || this.chat.isOpen()) return;
      if (!this.clientId) {
        this.showError("浏览器不支持聊天身份初始化");
        return;
      }
      if (!this.websocketUrl) {
        this.showError("聊天室地址未配置");
        return;
      }
      const separator = this.websocketUrl.includes("?") ? "&" : "?";
      this.chat.connect(`${this.websocketUrl}${separator}clientId=${encodeURIComponent(this.clientId)}`);
    },
    closeAll() {
      this.isEmoji = false;
    },
    toggleVoice() {
      this.isVoice = !this.isVoice;
      if (this.isVoice) this.isEmoji = false;
    },
    toggleEmoji() {
      if (this.isVoice) return;
      this.isEmoji = !this.isEmoji;
    },
    sendMessage() {
      if (!this.chat || !this.chat.sendText(this.draft)) {
        if (this.status !== "online") this.showError("聊天室尚未连接");
        return;
      }
      this.draft = "";
      this.isEmoji = false;
    },
    retryMessage(clientMessageId) {
      if (!this.chat?.retry(clientMessageId) && this.status !== "online") {
        this.showError("聊天室尚未连接");
      }
    },
    loadHistory() {
      if (!this.chat?.requestHistory()) this.showError("聊天室尚未连接");
    },
    recall(item) {
      if (!this.chat?.recall(item.id, item.type === 5)) this.showError("聊天室尚未连接");
    },
    async sendVoice(file) {
      if (!file || !this.clientId || this.voiceSending) return;
      this.voiceSending = true;
      const formData = new window.FormData();
      formData.append("file", file);
      formData.append("type", "5");
      formData.append("clientId", this.clientId);
      formData.append("clientMessageId", this.createMessageId());
      try {
        await this.$api.public.sendVoice(formData, {
          headers: { "Content-Type": "multipart/form-data" },
          suppressErrorToast: true
        });
      } catch {
        this.showError("语音发送失败，请重试");
      } finally {
        this.voiceSending = false;
      }
    },
    createMessageId() {
      return window.crypto?.randomUUID?.()
        || "voice-" + Date.now() + "-" + Math.random().toString(36).slice(2);
    },
    showError(message) {
      this.$toast?.({ type: "error", message });
    }
  }
};
</script>

<style>
@media (min-width: 760px) {
  .chat-container {
    bottom: 104px;
    height: calc(85% - 64px - 20px);
    max-height: 590px;
    min-height: 250px;
    right: 20px;
    width: 400px;
  }

  .chat-container .close {
    display: none;
  }
}

@media (max-width: 760px) {
  .chat-container {
    bottom: 0;
    left: 0;
    right: 0;
    top: 0;
  }

  .chat-container .close {
    display: block;
    margin-left: auto;
  }
}

.chat-container {
  background: #f4f6fb;
  box-shadow: 0 5px 40px rgba(0, 0, 0, 0.16);
  color: #4c4948;
  font-size: 14px;
  position: fixed;
  z-index: 1200;
}

.chat-btn {
  align-items: center;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border: 0;
  border-radius: 100px;
  bottom: 15px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16);
  cursor: pointer;
  display: flex;
  height: 60px;
  justify-content: center;
  position: fixed;
  right: 5px;
  user-select: none;
  width: 60px;
  z-index: 1000;
}

.message {
  bottom: 58px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 20px 16px 0;
  position: absolute;
  top: 80px;
  width: 100%;
}

.chat-status {
  color: #8a94a6;
  font-size: 12px;
  margin: 0 0 12px;
  text-align: center;
}

.history-button {
  background: #fff;
  border: 1px solid #d9dfe9;
  border-radius: 14px;
  color: #536174;
  cursor: pointer;
  display: block;
  font-size: 12px;
  margin: 0 auto 14px;
  padding: 5px 12px;
}

.history-button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.user-message,
.my-message {
  align-items: center;
  display: flex;
  margin-bottom: 9px;
}

.user-message {
  margin-right: 30px;
}

.my-message {
  justify-content: flex-end;
  margin-left: 30px;
}

.left-avatar {
  margin-right: 10px;
}

.right-avatar {
  margin-left: 10px;
  order: 1;
}

.user-avatar {
  border-radius: 50%;
  flex: 0 0 36px;
  height: 36px;
  object-fit: cover;
  width: 36px;
}

.nickname {
  align-items: center;
  display: flex;
  font-size: 12px;
  margin-bottom: 5px;
  margin-top: 3px;
}

.nickname span {
  color: #9aa3b2;
  margin-left: 12px;
}

.user-content,
.my-content {
  max-width: 260px;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.user-content {
  background: #fff;
  border-radius: 5px 20px 20px 20px;
  padding: 10px;
  white-space: pre-line;
}

.my-content {
  background: #12b7f5;
  border-radius: 8px;
  color: #fff;
  padding: 7px 9px;
  white-space: pre-line;
}

.voice-message {
  align-items: center;
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  display: flex;
  gap: 6px;
  padding: 0;
}

.voice-message audio {
  display: none;
}

.back-menu {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 2px;
  color: #000;
  display: none;
  font-size: 13px;
  height: 35px;
  line-height: 35px;
  position: absolute;
  text-align: center;
  width: 80px;
  z-index: 3;
}

.back-menu.visible {
  display: block;
}

.pending-message {
  opacity: 0.72;
}

.pending-label,
.retry-message {
  font-size: 11px;
}

.retry-message {
  background: transparent;
  border: 0;
  color: #fff;
  cursor: pointer;
  display: block;
  padding: 4px 0 0;
  text-decoration: underline;
}

.unread {
  align-items: center;
  background: #f24f2d;
  border-radius: 50%;
  color: #fff;
  display: flex;
  font-size: 12px;
  height: 20px;
  justify-content: center;
  min-width: 20px;
  position: absolute;
  right: -2px;
  top: -3px;
  z-index: 1;
}
</style>
