<template>
  <div class="mobile-editor-mode" role="group" aria-label="编辑器模式">
    <button
      type="button"
      class="mobile-editor-mode__button"
      :class="{ 'is-active': modelValue === 'edit' }"
      :aria-pressed="modelValue === 'edit'"
      @click="$emit('update:modelValue', 'edit')"
    >
      编辑
    </button>
    <button
      type="button"
      class="mobile-editor-mode__button"
      :class="{ 'is-active': modelValue === 'preview' }"
      :aria-pressed="modelValue === 'preview'"
      @click="$emit('update:modelValue', 'preview')"
    >
      预览
    </button>
  </div>
</template>

<script>
export default {
  name: "MobileEditorMode",
  props: {
    modelValue: {
      type: String,
      default: "edit"
    }
  },
  emits: ["update:modelValue"]
};
</script>

<style scoped>
.mobile-editor-mode {
  display: none;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .mobile-editor-mode {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
    width: 100%;
    padding: 4px;
    margin: 1rem 0;
    background: #f2f4f7;
    border: 1px solid #e5e7eb;
    border-radius: 11px;
  }

  .mobile-editor-mode__button {
    min-height: 38px;
    padding: 7px 12px;
    color: #6e6e73;
    font: inherit;
    font-size: 13px;
    font-weight: 650;
    background: transparent;
    border: 0;
    border-radius: 8px;
    cursor: pointer;
  }

  .mobile-editor-mode__button.is-active {
    color: #0071e3;
    background: #fff;
    box-shadow: 0 2px 7px rgba(29, 29, 31, 0.08);
  }
}
</style>
