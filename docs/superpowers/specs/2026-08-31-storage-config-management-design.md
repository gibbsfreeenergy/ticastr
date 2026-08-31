# 对象存储配置管理设计

**日期：** 2026-08-31  
**状态：** 待用户审阅  
**范围：** Spring Boot API、Flyway 数据库、Vue 管理端

## 目标

把 COS、OSS、TOS 以及 local 的运行时存储配置从“环境变量直接注入”改成“管理系统维护的 bucket/profile 配置”。系统允许保存多条配置，但任意时刻只有一条配置负责新对象；历史文章内容和媒体资产继续使用创建时绑定的配置。管理端可以验证配置，并查看每个来源的真实对象数量和已用字节数。

## 已确认的约束

- 来源类型包括 `local`、`cos`、`oss`、`tos`。
- 同一来源类型可以配置多个 profile，例如多个 COS bucket。
- 任意时刻只能有一个 active profile。
- 新上传只使用 active profile；切换不迁移旧对象。
- 历史资产必须记录具体 `storage_config_id`，不能只依赖 provider 类型。
- 访问凭据允许保存到数据库，但必须密文存储；API、日志和前端不回显明文。
- 加密主密钥由部署环境通过 `STORAGE_CONFIG_ENCRYPTION_KEY` 提供。
- 统计使用供应商对象列表的真实对象数量和大小；local 使用文件遍历汇总。
- 统计由管理员手动刷新，失败时保留上次成功快照并显示失败状态。
- 兼容旧部署：首次启动时一次性导入旧环境变量；导入完成后数据库是唯一配置来源。
- 现有工作区中与本需求无关的未提交阅读轨迹改动不属于本设计范围，实施时必须保留。

## 方案选择

### 采用：配置 profile + provider factory + 资产配置 ID

`tb_storage_provider_config` 演进为多行 profile 表。每个 profile 拥有稳定 ID、来源类型和 bucket/本地目录配置。provider factory 根据 profile 生成对应 SDK 适配器，registry 根据配置 ID 懒加载并缓存适配器。资产表保存 profile ID，因此同一 provider 的多个 bucket 之间切换不会破坏历史对象读取或删除。

### 不采用：在单例行中保存 JSON

这种方式无法可靠表达多条同厂商配置、难以对资产建立稳定外键，也会把查询、并发激活和统计快照耦合到一大段 JSON 中。

### 不采用：把凭据完全交给外部密钥服务

外部 KMS/Secrets Manager 可以降低数据库密钥暴露风险，但会增加一个当前需求没有要求的运行依赖。此版本使用数据库 AES-GCM 密文和一个部署主密钥，后续仍可替换为密钥服务而不改变管理 API。

## 数据模型

### `tb_storage_provider_config`

保留现有表名，取消 `id = 1` 的单例约束，使每行成为一个 profile。主要字段如下：

| 字段 | 说明 |
| --- | --- |
| `id` | `BIGINT AUTO_INCREMENT`，配置 ID |
| `config_name` | 管理端显示名称 |
| `provider` | `local`、`cos`、`oss` 或 `tos` |
| `endpoint` | 云端 API endpoint；local 为空 |
| `bucket` | 云端 bucket；local 为空 |
| `region` | 云端 region；local 为空 |
| `local_root` | local 根目录；云端为空 |
| `public_url` | 对外资源前缀，必须由服务端校验 |
| `access_key_id_ciphertext` | 加密后的访问标识；local 为空 |
| `access_key_secret_ciphertext` | 加密后的访问密钥；local 为空 |
| `is_active` | 是否为新对象使用的 profile |
| `config_source` | `DEFAULT`、`LEGACY_ENV` 或 `ADMIN` |
| `last_validation_status` | `NEVER`、`SUCCESS` 或 `FAILED` |
| `last_validation_at` | 最近验证时间 |
| `last_validation_message` | 脱敏后的验证结果 |
| `usage_status` | `NEVER`、`SUCCESS` 或 `FAILED` |
| `usage_object_count` | 最近成功统计的对象数 |
| `usage_bytes` | 最近成功统计的对象字节数 |
| `usage_last_modified` | 最近成功统计中对象的最大修改时间 |
| `usage_checked_at` | 最近统计时间 |
| `usage_error` | 脱敏后的统计错误 |
| `created_at`、`updated_at`、`updated_by` | 审计字段 |

`is_active` 通过 MySQL 生成列/唯一索引实现“active=1 时唯一、inactive 可以多行”的约束；服务层在切换时锁定配置表并原子更新。active 配置不能直接删除。配置仍被内容或媒体资产引用时，由外键拒绝删除。

字段校验规则：

- `local` 必须有 `config_name`、`local_root` 和 `public_url`，不需要 endpoint、region、bucket 或凭据。
- `cos`、`oss`、`tos` 必须有 `config_name`、endpoint、region、bucket、public URL、access key ID 和 access key secret。
- endpoint 只允许绝对 HTTP(S) URL，禁止凭据、查询串和 fragment；public URL 同样禁止凭据。
- local 根目录必须经过路径规范化；对象 key 仍由服务端生成并通过既有 `ObjectKeyPolicy` 校验。

### `tb_storage_bootstrap_state`

新增单例迁移状态表，记录旧环境变量导入是否完成，避免管理员新增配置后每次重启都重新导入。启动导入在短事务中锁定该行：

1. 状态为未完成时读取当前 profile 与旧 `StorageProperties`。
2. 将已配置的旧 local/COS/OSS/TOS 配置导入或更新为 `LEGACY_ENV` 并加密凭据。
3. 按旧 active provider 选择可用 profile；旧 active 配置不可用时回退到 local 默认 profile。
4. 为可以唯一确定 profile 的旧资产回填 `storage_config_id`。
5. 标记导入完成。

状态完成后，`STORAGE_ACTIVE_PROVIDER`、`OSS_*`、`COS_*`、`TOS_*` 只保留为迁移兼容输入，不再覆盖数据库配置。

### 资产关联

`tb_content_asset` 和 `tb_media_asset` 增加可回填的 `storage_config_id BIGINT`、索引和对 `tb_storage_provider_config(id)` 的外键。原有 `provider` 字段保留用于兼容和审计；新写入必须同时保存 provider 类型和配置 ID。文章内容的 provider+object key 唯一约束改为配置 ID+object key。

旧资产回填策略：当同一 provider 恰好只有一个可用 profile 时直接回填；无法唯一确定时保持 NULL，读取/删除使用 provider 类型兼容回退并写出告警。首次导入旧环境变量通常会先创建唯一 profile，从而覆盖正常旧部署的资产。

## 加密与安全边界

新增 `StorageConfigCrypto`：

- 主密钥由 `STORAGE_CONFIG_ENCRYPTION_KEY` 读取，要求 Base64 编码的 32 字节 AES key。
- 每个字段使用随机 12 字节 nonce 和 AES-GCM/NoPadding 加密，密文包含版本、nonce 和 ciphertext，便于未来轮换格式。
- 主密钥缺失时，local-only 配置可以启动；读取或写入加密云凭据时立即以安全错误失败。生产-like 环境在存在云 profile 时必须配置主密钥。
- 解密只在创建 SDK client 的短生命周期内发生；不把明文写入 DTO、日志、异常、缓存序列化或浏览器状态。
- 编辑请求中的凭据为空表示保留原密文；非空值替换原密文。前端只显示“已设置”。

## 运行时架构

### Provider port

扩展 provider-neutral `StorageProvider` 合同，增加 `usage()`，返回：

- `objectCount`
- `usedBytes`
- `latestObjectModified`

COS、OSS、TOS 使用各自 SDK 的分页列表接口；local 使用 `Files.walk` 统计普通文件。所有实现都设置页大小和总耗时边界，统计只通过管理端显式触发。供应商 SDK 仍不能越过 media port 进入业务服务。

### Factory 与 registry

- `StorageProviderFactory` 接受已经校验的 profile snapshot，按 provider 创建 local/COS/OSS/TOS adapter。
- `StorageProviderRegistry` 以配置 ID 为主键懒加载 provider client，并提供 `providerForNewAsset()`、`providerForConfig(id)` 和 `publicReference(configId, objectKey)`。
- client 在首次对象操作、验证或统计时创建；应用启动、读取配置列表和切换 active 不访问云端。
- 配置被编辑或激活时使对应 client 失效；provider 关闭由 registry 负责，避免手工创建的 SDK client 泄漏。
- 资产读取、删除和存在性检查优先按 `storage_config_id` 解析；只有旧 NULL 记录才按 provider 唯一 profile 或 active provider 回退。

### Active 切换

1. 管理服务校验目标 profile 的字段完整性。
2. 使用目标 profile 创建临时 provider，执行 bucket/root 检查、临时对象写入、读取、删除和删除后存在性检查。
3. 验证失败不修改 active 行，并持久化失败验证结果。
4. 验证成功后在数据库事务中锁表、取消旧 active、设置目标 active，写入操作用户和时间。
5. 事务提交后刷新 registry；历史 profile 的 client 仍可按资产 ID使用。

修改当前 active profile时，先用候选配置执行验证，验证成功后才更新数据库和 registry；inactive profile 可以先保存，再单独验证或激活。

## API 设计

### 配置管理

```text
GET    /api/admin/storage/configs
POST   /api/admin/storage/configs
PUT    /api/admin/storage/configs/{id}
DELETE /api/admin/storage/configs/{id}
POST   /api/admin/storage/configs/{id}/validate
POST   /api/admin/storage/configs/{id}/activate
POST   /api/admin/storage/configs/{id}/usage
```

创建/更新请求字段：`name`、`provider`、`endpoint`、`region`、`bucket`、`localRoot`、`publicUrl`、`accessKeyId`、`accessKeySecret`。字段按 provider 条件校验；更新时空凭据表示保留原值。

列表和操作响应只包含安全摘要：配置 ID、名称、来源类型、active、endpoint/bucket 或 local root、region、public URL、`configured`、`credentialsConfigured`、验证快照和 usage 快照。响应不包含任何密文、明文 credential、签名、任意 object key 或供应商内部请求 URL。

usage 快照成功示例：

```json
{
  "status": "SUCCESS",
  "objectCount": 128,
  "usedBytes": 7340032,
  "latestObjectModified": "2026-08-31T08:20:00Z",
  "checkedAt": "2026-08-31T08:21:00Z"
}
```

旧接口 `/api/admin/storage/provider`、`/api/admin/storage/providers` 和旧 provider 切换接口保留兼容。兼容切换只在目标 provider 恰好对应一个 profile 时执行；多个 profile 时返回 409，提示使用配置 ID，新管理页面不再调用旧接口。

### 错误协议

- 400：字段缺失、provider 不支持、URL/路径不合法。
- 404：配置 ID 不存在。
- 409：删除 active、删除仍被资产引用的配置、active 唯一约束冲突、旧接口无法在多个 profile 中选择目标。
- 503：候选配置真实验证或 usage 依赖不可用。
- 500：未预期错误，响应只使用通用系统错误信息。

供应商 SDK 原始异常只进入带配置 ID 的服务端日志；错误信息先脱敏，不能包含 access key、secret、Authorization、签名、完整请求 URL 或堆栈。

## 管理端设计

在现有“设置 -> 基础设施”页面中替换 provider radio/table：

- 表格/卡片展示配置名称、来源、bucket 或根目录、region、active、凭据状态、验证状态和容量统计。
- 新增/编辑弹窗先选择来源，再显示对应字段；local 隐藏云端字段，云来源隐藏 local root。
- 编辑时 access key 和 secret 默认空白；空白提交保留原密文。
- 每行提供编辑、删除、验证、激活、刷新使用量操作；active 行不能删除。
- 容量显示对象数量、已用字节、最近对象时间和统计时间；失败显示上次成功值与本次错误状态。
- 激活按钮在验证成功后刷新列表和 active 标识；切换失败不改变页面中的当前 active 状态。
- 继续使用 Vue Options API、相对 `/api` 请求和现有 Element 组件风格。
- 前端状态、测试快照和错误展示不得包含凭据字段值。

## 权限与迁移

下一条 Flyway migration（当前最新为 V20，因此使用 V21）负责：

1. 演进 `tb_storage_provider_config` 为 profile 表并保留旧行数据。
2. 建立单 active 约束和 bootstrap 状态表。
3. 给内容/媒体资产增加配置 ID 和外键。
4. 新增配置 CRUD、验证、激活和 usage 刷新资源权限。
5. 为菜单/资源契约补充 `/admin/storage/configs...` 路由；保留旧资源用于滚动发布。

更新 `application.yml`、本地示例、`.env.example`、配置字典、API 契约、媒体生命周期和运维手册：新增加密主密钥，标记旧存储环境变量为一次性迁移输入，并记录“切换只影响新对象”的规则。不得把真实凭据写入示例或迁移。

## 验收与测试

### 后端单元测试

- AES-GCM 密文可解密、每次 nonce 不同、篡改和错误主密钥拒绝。
- profile 条件校验、凭据留空保留、单 active、并发切换和删除保护。
- provider factory/registry 按配置 ID 路由，构造和刷新不产生网络调用。
- active 切换后旧 content/media asset 仍调用原 profile。
- 各 provider usage 分页累计对象数、字节数和最大修改时间；统计失败保留旧快照。
- 序列化配置摘要不含 `secret`、明文 key、密文、Authorization 或内部错误细节。

### 数据库集成测试

- fresh schema 可完成全部 Flyway migration。
- legacy schema 可升级，旧环境变量只导入一次，active provider 迁移正确。
- 内容和媒体资产的 `storage_config_id` 可回填，历史记录受外键保护。
- 同一来源多 profile 可共存且数据库只允许一个 active。

### 管理端测试

- 来源选择控制 local/cloud 字段显示。
- 新增、编辑、空凭据保留、删除确认、验证、激活和 usage 刷新状态流转。
- active/统计展示正确，接口响应和组件状态不回显凭据。

### 完成前命令

```text
cd blog-springboot && mvn test
cd blog-vue/admin && npm run test
cd blog-vue/admin && npm run build
git diff --check
```

真实 COS/OSS/TOS 连通性测试仅在受控凭据环境中运行；默认测试使用 fake provider 或对象存储模拟器，不依赖真实云账号。
