<template>
  <div
    ref="messageList"
    class="message"
    role="log"
    aria-live="polite"
    aria-label="聊天室消息"
    @contextmenu.prevent.stop="$emit('close-menu')"
  >
    <button v-if="hasMore" type="button" class="history-button" :disabled="historyLoading" @click="$emit('load-history')">
      {{ historyLoading ? "正在加载历史消息…" : "加载更早消息" }}
    </button>
    <p v-if="status !== 'online'" class="chat-status" role="status">{{ statusLabel }}</p>
    <div
      v-for="item in messages"
      :key="messageKey(item)"
      :class="isSelf(item) ? 'my-message' : 'user-message'"
    >
      <img
        :src="item.avatar"
        :class="isSelf(item) ? 'user-avatar right-avatar' : 'user-avatar left-avatar'"
        :alt="(item.nickname || '访客') + '的头像'"
        width="36"
        height="36"
        loading="lazy"
      />
      <div>
        <div v-if="!isSelf(item)" class="nickname">
          {{ item.nickname || "访客" }}
          <span>{{ hour(item.createTime) }}</span>
        </div>
        <div
          :class="isSelf(item) ? 'my-content' : 'user-content'"
          @contextmenu.prevent.stop="showBack(item, $event)"
        >
          <div v-if="item.type === 3" v-safe-html="item.content" />
          <button v-else-if="item.type === 5" type="button" class="voice-message" @click="playVoice(item)">
            <audio
              :ref="element => setVoiceRef(item, element)"
              :src="item.content"
              preload="metadata"
              @ended="endVoice(item)"
              @loadedmetadata="getVoiceTime(item, $event)"
            />
            <v-icon
              :color="isSelf(item) ? '#fff' : '#000'"
              :icon="playingVoiceId === item.id ? '$mdi-pause-circle' : '$mdi-arrow-right-drop-circle'"
            />
            <span>{{ voiceDurations[item.id] || "播放语音" }}</span>
          </button>
          <button
            v-if="backMenuId === item.id && isSelf(item)"
            type="button"
            class="back-menu visible"
            @click="$emit('recall', item)"
          >
            撤回
          </button>
        </div>
      </div>
    </div>
    <div
      v-for="item in pendingMessages"
      :key="item.clientMessageId"
      class="my-message pending-message"
    >
      <div class="my-content">
        <div v-safe-html="item.content" />
        <button v-if="item.status === 'failed'" type="button" class="retry-message" @click="$emit('retry', item.clientMessageId)">
          发送失败，重试
        </button>
        <span v-else class="pending-label">发送中…</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "ChatMessageList",
  emits: ["close-menu", "load-history", "recall", "retry"],
  props: {
    messages: { type: Array, default: () => [] },
    pendingMessages: { type: Array, default: () => [] },
    hasMore: { type: Boolean, default: false },
    historyLoading: { type: Boolean, default: false },
    status: { type: String, default: "offline" }
  },
  data() {
    return {
      playingVoiceId: null,
      voiceDurations: {},
      voicePlayers: {},
      backMenuId: null
    };
  },
  computed: {
    statusLabel() {
      return this.status === "reconnecting" ? "连接断开，正在重试…" : "聊天室暂不可用";
    }
  },
  beforeUnmount() {
    Object.values(this.voicePlayers).forEach(player => {
      player.pause();
      player.currentTime = 0;
    });
    this.voicePlayers = {};
  },
  methods: {
    messageKey(item) {
      return item.id || item.messageId || item.clientMessageId;
    },
    isSelf(item) {
      return item.owner === true;
    },
    hour(value) {
      if (!value) return "";
      return new Date(value).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    },
    setVoiceRef(item, element) {
      if (element) this.voicePlayers[item.id] = element;
      else delete this.voicePlayers[item.id];
    },
    playVoice(item) {
      const player = this.voicePlayers[item.id];
      if (!player) return;
      if (player.paused) {
        Object.entries(this.voicePlayers).forEach(([id, other]) => {
          if (id !== String(item.id)) other.pause();
        });
        this.playingVoiceId = item.id;
        player.play().catch(() => {
          if (this.playingVoiceId === item.id) this.playingVoiceId = null;
        });
      } else {
        player.pause();
        this.playingVoiceId = null;
      }
    },
    endVoice(item) {
      if (this.playingVoiceId === item.id) this.playingVoiceId = null;
    },
    getVoiceTime(item, event) {
      const duration = event.target.duration;
      if (Number.isFinite(duration)) this.voiceDurations[item.id] = Math.ceil(duration) + "''";
    },
    showBack(item) {
      this.backMenuId = item.owner ? item.id : null;
    }
  }
};
</script>
