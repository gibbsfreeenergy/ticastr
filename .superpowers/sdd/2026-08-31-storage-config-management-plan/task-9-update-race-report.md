# Task 9：StorageConfigAdminService 更新竞态修复

## Scope

基于 `d07b308` 后的实际代码，仅修复 managed storage profile `update(id, request)` 的并发一致性边界；未修改 provider 生命周期、bootstrap、DAO/XML 或 frontend。报告不包含凭据、密文、令牌或外部服务敏感信息。

## Implementation

- 候选配置构造及 active profile 的临时 provider 网络校验仍在事务外执行，避免网络调用持有数据库行锁。
- 候选完成后，`update` 通过与现有 `activate/delete` 相同的 `TransactionTemplate`/null 兼容分支进入写入阶段。
- 写入阶段先对目标执行 `selectByIdForUpdate`，再将锁后目标与最初读取的 expected profile 做 `sameProfile` 比对；比对覆盖 ID、连接字段、凭据密文、active、source、createdAt、updatedAt 和 updatedBy。
- 目标在候选构造或网络校验后发生变化时抛出安全 `ConflictException`，不执行 `updateProfile`，也不失效 registry。目标一致时才执行 profile update。
- 保留既有语义：active 候选验证失败不写库；active 成功写入成功 validation snapshot 并清空 usage snapshot；inactive 更新清除 validation/usage snapshot。

## Tests

`StorageConfigAdminServiceTest` 新增/调整覆盖：

- inactive 候选构造后目标被 activate，更新被拒绝且不写库；
- active 候选网络验证后目标被修改，更新被拒绝且不写库；
- active/inactive 成功路径补充 `selectByIdForUpdate` stubbing，并验证 provider 关闭后才锁目标、锁后且一致检查通过后才执行 `updateProfile`；
- 保留 active 验证失败、成功清空 usage，以及 inactive 清除旧快照的既有断言。

## Verification

- `cd blog-springboot && mvn -Dtest=StorageConfigAdminServiceTest test` — **通过，17 tests，0 failures/errors**。
- `git diff --check` — **通过**。
- `cd blog-springboot && mvn test` — 已启动并通过前序测试，但在 DAO 集成测试启动期间按用户要求主动中止；未将未完成的完整套件宣称为通过。

## Commit

最终 commit hash 见交接信息。
