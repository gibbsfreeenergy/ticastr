<template>
  <main class="not-found">
    <h1>404</h1>
    <p>页面不存在，或当前账号没有访问该页面的权限。</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </main>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: center;
  color: #606266;
}

h1 {
  margin: 0;
  font-size: 6rem;
  color: #409eff;
}
</style>
