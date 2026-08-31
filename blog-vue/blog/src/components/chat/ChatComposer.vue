<template>
  <footer class="chat-footer" @click.stop>
    <div v-if="isEmoji" class="emoji-box">
      <Emoji :chooseEmoji="true" @addEmoji="appendEmoji" />
    </div>
    <button
      type="button"
      class="composer-icon"
      :aria-label="isVoice ? '切换文字输入' : '切换语音输入'"
      :title="isVoice ? '切换文字输入' : '切换语音输入'"
      @click="$emit('toggle-voice')"
    >
      <v-icon :icon="isVoice ? '$mdi-keyboard' : '$mdi-microphone'" />
    </button>
    <textarea
      v-if="!isVoice"
      ref="input"
      :value="modelValue"
      class="chat-input"
      rows="1"
      maxlength="1000"
      placeholder="请输入内容"
      aria-label="聊天内容"
      @input="$emit('update:modelValue', $event.target.value)"
      @keydown="handleKeydown"
    />
    <VoiceRecorder
      v-else
      class="voice-recorder"
      :disabled="disabled"
      @recorded="$emit('voice-recorded', $event)"
      @error="$emit('error', $event)"
    />
    <button
      type="button"
      class="composer-icon emoji-toggle"
      :class="{ active: isEmoji }"
      aria-label="打开表情"
      title="表情"
      @click="$emit('toggle-emoji')"
    >
      <span aria-hidden="true">☺</span>
    </button>
    <button
      type="button"
      class="composer-icon send-button"
      :disabled="disabled || !modelValue.trim() || isVoice"
      aria-label="发送消息"
      title="发送消息"
      @click="$emit('send')"
    >
      <span aria-hidden="true">➤</span>
    </button>
  </footer>
</template>

<script>
import Emoji from "../Emoji";
import VoiceRecorder from "./VoiceRecorder";

export default {
  name: "ChatComposer",
  components: { Emoji, VoiceRecorder },
  emits: ["update:modelValue", "send", "toggle-voice", "toggle-emoji", "voice-recorded", "error"],
  props: {
    modelValue: { type: String, default: "" },
    isVoice: { type: Boolean, default: false },
    isEmoji: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false }
  },
  methods: {
    handleKeydown(event) {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        this.$emit("send");
      }
    },
    appendEmoji(value) {
      const current = this.modelValue || "";
      this.$emit("update:modelValue", current + value);
      this.$emit("toggle-emoji");
      this.$nextTick(() => this.$refs.input?.focus());
    }
  }
};
</script>

<style scoped>
.chat-footer {
  align-items: center;
  background: #f7f7f7;
  border-radius: 0 0 1rem 1rem;
  bottom: 0;
  display: flex;
  gap: 6px;
  min-height: 50px;
  padding: 8px 12px;
  position: absolute;
  width: 100%;
}

.chat-input {
  background: #fff;
  border: 1px solid #d9dfe9;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 13px;
  height: 34px;
  line-height: 20px;
  outline: none;
  padding: 6px 9px;
  resize: none;
  width: 100%;
}

.chat-input:focus {
  border-color: #7398ea;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.composer-icon {
  align-items: center;
  background: transparent;
  border: 0;
  color: #536174;
  cursor: pointer;
  display: inline-flex;
  flex: 0 0 28px;
  height: 32px;
  justify-content: center;
  padding: 0;
}

.composer-icon:hover,
.composer-icon.active,
.send-button:not(:disabled) {
  color: #2563eb;
}

.composer-icon:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.emoji-toggle {
  font-size: 22px;
  line-height: 1;
}

.send-button {
  font-size: 22px;
  line-height: 1;
}

.emoji-box {
  background: #fff;
  border-radius: 8px;
  bottom: 52px;
  box-shadow: 0 8px 16px rgba(50, 50, 93, 0.08), 0 4px 12px rgba(0, 0, 0, 0.07);
  max-height: 180px;
  overflow-y: auto;
  padding: 6px 16px;
  position: absolute;
  right: 20px;
  width: 328px;
  z-index: 2;
}

.voice-recorder {
  flex: 1;
}

@media (max-width: 420px) {
  .chat-footer {
    gap: 2px;
    padding-left: 8px;
    padding-right: 8px;
  }

  .emoji-box {
    left: 8px;
    right: 8px;
    width: auto;
  }
}
</style>
