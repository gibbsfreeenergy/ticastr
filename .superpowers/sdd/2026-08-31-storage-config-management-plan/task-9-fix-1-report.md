# Task 9 Fix Round 1

## Scope

修复 `StorageConfigAdminService` 在 managed storage profile 更新和激活流程中的配置一致性问题。实现保持 controller -> service -> DAO 分层，并保留原有三参数构造器及 transaction-manager 四参数构造器。

## Implementation

- 为管理服务接入 `StorageProviderFactory`。配置校验通过 factory 从候选 entity 创建临时 provider，校验结束后在 `finally` 中关闭；候选配置不会先进入 `StorageProviderRegistry` 的持久缓存。
- active profile update 在 `updateProfile` 前执行真实 `validateConnection`。校验失败抛出安全的 `ConflictException`，不调用 `updateProfile`，也不失效当前 registry provider；成功后把新的 `SUCCESS` validation snapshot 与配置一起持久化。
- 任意 profile 配置变更都会把 usage snapshot 重置为 `NEVER` 和空值；inactive profile 的旧 validation snapshot 也会重置为 `NEVER`/空值，避免旧连接字段对应的真实数据继续展示。
- 扩展 `StorageProviderConfigDao.xml` 的 `updateProfile`，使 validation/usage snapshot 的清理和成功结果实际写入数据库。
- activate 的网络校验保持在事务外；进入现有短事务后继续先锁 target row、再锁 active row，并比对候选与锁后 target 的 ID、连接字段、凭据密文、active/source、`updatedAt` 和 `updatedBy`。发生并发更新时抛出安全冲突，不执行 `activateOnly`；一致时才记录校验成功并切换 active。

## Focused tests

在 `StorageConfigAdminServiceTest` 增加并覆盖：

- active update 校验失败不写库、不失效当前 provider；
- active update 按临时 provider 校验并关闭后才 `updateProfile`，成功 snapshot 写入且 usage 清空；
- inactive update 清除过期 validation/usage snapshot；
- activate 在网络校验后发现锁定 target 已变化时不执行 `activateOnly`。

## Verification

- `cd blog-springboot && mvn -Dtest=StorageConfigAdminServiceTest test` — 15 tests passed。
- `git diff --check` — passed。

按本轮“优先提交当前修复”的要求，未重复运行整套后端、前端测试或可能启动 Docker 的 DAO 集成测试；本轮保留了针对上述竞态和快照行为的完整聚焦测试证据。

## Commits

- Implementation: `9fbed5a` (`fix: validate managed storage profile updates safely`)

本轮 worktree 中其他 `.env`、bootstrap 和配置相关未提交改动未被修改、未被暂存或提交。
