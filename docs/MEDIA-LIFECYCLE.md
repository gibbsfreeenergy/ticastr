# 媒体与文章内容生命周期

媒体和 Markdown 内容都位于对象存储，MySQL 只保存资产 ledger、provider、object key、checksum、size、版本和状态。业务服务依赖 MediaAssetStore/StorageProvider，不直接依赖云 SDK，也不把 provider URL 决策放进浏览器。

## 对象 key 与 provider

- 普通媒体由服务端生成 media/{yyyy}/{MM}/{uuid}.{ext}。
- 文章内容使用 articles/{articleId}/{version}-{assetId}.md。
- key 不接受调用方任意传入，内容和媒体均记录创建时 provider。
- active provider 只影响之后的新对象；local、OSS、COS、TOS 同时支持配置，但同一时间只选择一个。

## 内容版本

编辑器通过 /admin/articles/{id}/content 写入新的不可变 Markdown 对象，MySQL 事务更新当前指针；expectedVersion 防止覆盖并发编辑。公开读取 /articles/{id}/content 只返回 Markdown 流并带 ETag/Last-Modified，文章列表只返回 metadata 和 content pointer。

旧版本保留在 ledger，恢复操作读取旧对象后写入一个新的版本。对象读取失败不会返回空正文。

## 删除与对账

PENDING -> RETIRED -> DELETE_PENDING -> DELETED；删除失败进入 DELETE_FAILED。任何删除前都要通过有界、带索引的引用检查，保护文章内容、文章封面、相册、照片、说说、网站配置、头像等引用。provider 删除通过 MEDIA_DELETE/CONTENT_DELETE Outbox handler 异步执行并保持幂等。

reconciliation job 按页扫描 PENDING/RETIRED/DELETE_FAILED 和过期临时资产，创建或重试 Outbox，不在持有数据库事务时调用 provider。对账和清理必须保留失败原因、时间和 provider 延时，后台只允许针对已识别的资产/event 重试，不提供任意 object key 删除。

## 失败恢复

上传成功但 DB 发布失败：标记资产失败并尝试删除；删除失败由 reconciliation 接管。DB 已提交但异步删除未完成：资产保持 ledger 记录，不因 active provider 切换而误删。provider 临时不可用时修复凭据/网络后重试，不能手动修改 checksum 或 object key 伪造成功。
