<template>
  <button
    type="button"
    class="voice-btn"
    :class="{ recording: status === 'recording' || status === 'starting' }"
    :disabled="disabled"
    @pointerdown="start"
    @pointerup="stop"
    @pointercancel="cancel"
    @pointerleave="cancel"
  >
    {{ status === "recording" ? "松开发送" : status === "starting" ? "正在准备麦克风…" : "按住说话" }}
  </button>
</template>

<script>
import Recorderx, { ENCODE_TYPE } from "recorderx";

function cleanupRecorder(recorder) {
  if (!recorder) return;
  try {
    if (recorder.stream) recorder.pause();
  } catch {
    // A cancelled getUserMedia request may not have created a stream yet.
  }
  try {
    recorder.source?.disconnect?.();
    recorder.recorder?.disconnect?.();
    recorder.ctx?.close?.();
    recorder.clear?.();
  } catch {
    // Cleanup is best effort; no media object is retained after this point.
  }
}

export default {
  name: "VoiceRecorder",
  emits: ["recorded", "error"],
  props: {
    disabled: { type: Boolean, default: false }
  },
  data() {
    return {
      recorder: null,
      startedAt: 0,
      status: "ready"
    };
  },
  beforeUnmount() {
    this.cancel();
  },
  methods: {
    async start(event) {
      event.currentTarget?.setPointerCapture?.(event.pointerId);
      if (this.disabled || this.recorder) return;
      const recorder = new Recorderx();
      this.recorder = recorder;
      this.status = "starting";
      try {
        await recorder.start();
        if (this.recorder !== recorder) {
          cleanupRecorder(recorder);
          return;
        }
        this.startedAt = Date.now();
        this.status = "recording";
      } catch {
        if (this.recorder === recorder) {
          cleanupRecorder(recorder);
          this.recorder = null;
          this.status = "ready";
          this.$emit("error", "无法开始录音，请检查麦克风权限");
        }
      }
    },
    stop(event) {
      event.currentTarget?.releasePointerCapture?.(event.pointerId);
      if (this.status !== "recording" || !this.recorder) return;
      const recorder = this.recorder;
      const elapsed = Date.now() - this.startedAt;
      let file = null;
      try {
        if (elapsed < 1000) {
          this.$emit("error", "按键时间太短");
        } else {
          recorder.pause();
          const wav = recorder.getRecord({ encodeTo: ENCODE_TYPE.WAV });
          file = new File([wav], "voice.wav", { type: wav.type || "audio/wav" });
        }
      } catch {
        this.$emit("error", "录音处理失败，请重试");
      } finally {
        cleanupRecorder(recorder);
        this.recorder = null;
        this.startedAt = 0;
        this.status = "ready";
      }
      if (file) this.$emit("recorded", file);
    },
    cancel(event) {
      if (event?.currentTarget) event.currentTarget.releasePointerCapture?.(event.pointerId);
      if (!this.recorder) {
        this.status = "ready";
        return;
      }
      const recorder = this.recorder;
      this.recorder = null;
      this.startedAt = 0;
      this.status = "ready";
      cleanupRecorder(recorder);
    }
  }
};
</script>

<style scoped>
.voice-btn {
  width: 100%;
  height: 32px;
  border: 1px solid #d9dfe9;
  border-radius: 4px;
  background: #fff;
  color: #536174;
  cursor: pointer;
}

.voice-btn.recording {
  border-color: #ef6b5b;
  background: #fff3f1;
  color: #d94841;
}

.voice-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
