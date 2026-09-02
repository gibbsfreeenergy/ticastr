# Task 9 UI Polish Report

## 变更

- `StorageConfigPanel.vue` 保留桌面端 `width="560px"`，为 Element Plus dialog 增加 `storage-config-dialog` class，并通过 scoped `:deep` CSS 设置 `max-width: calc(100vw - 32px)`，避免约 390px 视口横向溢出。
- 使用量失败状态继续显示固定安全文案；当 allowlist 中的详情恰好也是 `使用量刷新失败` 时不再重复渲染。验证和使用量 allowlist 未放宽、未修改。
- `StorageConfigPanel.test.js` 增加 dialog class、桌面宽度和窄屏 max-width CSS 约束断言，并断言使用量失败文案只出现一次。

## 验证

- `npm run test:run -- src/views/setting/StorageConfigPanel.test.js`：通过，1 个文件、8 个测试。
- `npm run lint`（`blog-vue/admin`）：通过，退出码 0。
- `git diff --check`：通过；仅有仓库现有的 LF/CRLF 转换警告，无 whitespace 错误。
- 页面快速检查：当前 worktree 的 Vite 页面使用 `http://127.0.0.1:8082/`（8081 已被占用），成功加载并重定向到 `/login`，页面非空、标题为“后台管理系统”，无框架错误或 console warning/error。由于没有管理员会话，未在浏览器中打开 Settings dialog；组件测试覆盖了本次 dialog 样式与提示渲染约束。

## 范围

仅修改本报告、`StorageConfigPanel.vue` 和 `StorageConfigPanel.test.js`；未修改后端或其他 frontend 文件。按委托未运行整套 admin 测试和 build。
