<template>
  <el-card class="main-card">
    <StorageConfigPanel />
  </el-card>
</template>

<script>
import StorageConfigPanel from "../setting/StorageConfigPanel.vue";

export default {
  name: "Storage",
  components: { StorageConfigPanel }
};
</script>
