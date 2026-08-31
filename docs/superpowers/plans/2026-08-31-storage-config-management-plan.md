# Managed Object Storage Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move local/COS/OSS/TOS storage selection and bucket credentials into encrypted, admin-managed profiles while preserving historical asset routing and exposing real usage snapshots.

**Architecture:** Evolve `tb_storage_provider_config` from a singleton provider selector into a multi-row profile catalog with a database-enforced single active row. A provider-neutral factory creates lazy SDK adapters from decrypted profile snapshots; content and media ledgers persist the profile ID so provider switches never redirect historical objects. The admin API owns CRUD, validation, activation, and usage refresh, while a one-time bootstrap imports legacy environment configuration.

**Tech Stack:** Java 21, Spring Boot 4.1, MyBatis-Plus, MySQL 8, Flyway, Aliyun OSS SDK 3.8.0, Tencent COS SDK 5.6.75, Volcengine TOS SDK 2.8.8, Vue 3 Options API, Element Plus, Vite, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-31-storage-config-management-design.md`

## Global Constraints

- Sources are exactly `local`, `cos`, `oss`, and `tos`; multiple profiles may use the same source type.
- At most one profile is active; new objects use it and historical objects use their persisted `storage_config_id`.
- Credentials are AES-GCM ciphertext in MySQL; `STORAGE_CONFIG_ENCRYPTION_KEY` is the only deployment secret for this feature.
- API responses, frontend state, logs, and error messages must not contain plaintext credentials, ciphertext, authorization headers, signatures, or provider request URLs.
- Cloud usage is the actual paginated object count/byte sum; local usage is the actual regular-file count/byte sum.
- Usage is explicitly refreshed by an administrator; failed refreshes retain the previous successful snapshot.
- Legacy storage environment variables are imported once only when bootstrap state is incomplete; they never override an admin-managed profile afterward.
- Provider SDK clients are lazy and must not make network calls at application construction or startup.
- Keep backend layering `controller -> service -> dao`, keep entity/DTO/VO types distinct, keep frontend requests relative, preserve Vue Options API/double quotes, and preserve unrelated existing worktree changes.
- Do not add real credentials, production endpoints, `target`, `dist`, `node_modules`, IDE settings, `.env`, or local configuration to Git.

## File and Responsibility Map

Create these focused files:

- `blog-springboot/src/main/java/com/wzh/blog/config/StorageConfigCrypto.java`: AES-GCM encryption/decryption boundary.
- `blog-springboot/src/main/java/com/wzh/blog/media/StorageUsage.java`: provider-neutral usage result.
- `blog-springboot/src/main/java/com/wzh/blog/media/StorageProviderConfigSnapshot.java`: internal, redacted runtime profile snapshot.
- `blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/StorageProviderFactory.java`: constructs the adapter for one profile.
- `blog-springboot/src/main/java/com/wzh/blog/administration/StorageConfigAdminService.java`: profile CRUD, validation, activation, usage, and safe summaries.
- `blog-springboot/src/main/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunner.java`: one-time legacy environment import and asset-ID backfill.
- `blog-springboot/src/main/java/com/wzh/blog/entity/StorageBootstrapState.java`: persisted one-time import state and legacy active-provider hint.
- `blog-springboot/src/main/java/com/wzh/blog/dao/StorageBootstrapStateDao.java`: bootstrap state mapper.
- `blog-springboot/src/main/resources/mapper/StorageBootstrapStateDao.xml`: bootstrap state SQL.
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigRequest.java`: create/update input.
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageUsageVO.java`: usage response.
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageValidationVO.java`: safe validation response.
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigSummaryVO.java`: safe profile response.
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigListResponse.java`: active ID plus profile summaries.
- `blog-springboot/src/main/resources/db/migration/V21__managed_storage_profiles.sql`: profile catalog, bootstrap state, asset foreign keys, and resource permissions.
- `blog-vue/admin/src/views/setting/StorageConfigPanel.vue`: profile list and editor UI.
- `blog-vue/admin/src/views/setting/StorageConfigPanel.test.js`: admin profile UI contract tests.

Modify these existing boundaries:

- `StorageProviderConfig.java`, `StorageProviderConfigDao.java`, `StorageProviderConfigDao.xml`: profile fields and mapper operations.
- `StorageProperties.java`, `application.yml`, `application-local.example.yml`: encryption key and legacy-only bindings.
- `StorageProvider.java`, `StorageProviderRegistry.java`, `StorageProviderConfiguration.java`, all four storage adapters: usage, factory-created clients, config-ID routing, and lifecycle.
- `ContentAsset.java`, `ContentAssetDao.java`, `ContentAssetDao.xml`, `ContentAssetPersistenceService.java`, `ArticleContentServiceImpl.java`, `ContentAssetCleanupHandler.java`: content asset profile IDs.
- `MediaAssetLedger.java`, `DatabaseMediaAssetLedger.java`, `UploadStrategyContext.java`, media cleanup classes: media asset profile IDs.
- `StorageProviderController.java` and existing storage VOs: new endpoints plus deprecated compatibility endpoints.
- `DeploymentConfigurationValidator.java`: validate the database-selected profile and encryption key instead of using the environment-selected provider.
- `blog-vue/shared/api/createApi.js`, `Setting.vue`: new admin API and panel integration.
- `docs/API-CONTRACT.md`, `docs/CONFIGURATION.md`, `docs/MEDIA-LIFECYCLE.md`, `docs/OPERATIONS-RUNBOOK.md`, `.env.example`, and relevant Compose examples: operational contract.

---

### Task 1: Add encrypted credential and provider-neutral usage primitives

**Files:**
- Create: `blog-springboot/src/main/java/com/wzh/blog/config/StorageConfigCrypto.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/media/StorageUsage.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/media/StorageProviderConfigSnapshot.java`
- Test: `blog-springboot/src/test/java/com/wzh/blog/config/StorageConfigCryptoTest.java`

**Interfaces:**
- Produces `StorageConfigCrypto.encrypt(String)`, `StorageConfigCrypto.decrypt(String)`, `StorageConfigCrypto.hasKey()`, `StorageUsage`, and `StorageProviderConfigSnapshot` for later DAO/factory tasks.
- `StorageProviderConfigSnapshot` fields are `Long id`, `String name`, `StorageProviderType provider`, `String endpoint`, `String bucket`, `String region`, `String localRoot`, `String publicUrl`, `String accessKeyId`, and `String accessKeySecret`; override `toString()` with only ID/name/provider so accidental logging is safe.

- [ ] **Step 1: Write the failing crypto tests.** Add tests that use a fixed 32-byte Base64 key, assert two encryptions of the same value differ, assert decrypt returns the original, assert tampered ciphertext and an incorrect key throw a safe `IllegalStateException`, and assert a blank key reports unavailable.

```java
@Test
void encryptsWithAUniqueNonceAndDecryptsTheOriginal() {
    StorageConfigCrypto crypto = new StorageConfigCrypto(base64Key());

    String first = crypto.encrypt("secret-value");
    String second = crypto.encrypt("secret-value");

    assertNotEquals(first, second);
    assertNotEquals("secret-value", first);
    assertEquals("secret-value", crypto.decrypt(first));
}

@Test
void rejectsTamperedCiphertextAndMissingKey() {
    StorageConfigCrypto crypto = new StorageConfigCrypto(base64Key());
    String ciphertext = crypto.encrypt("secret-value");

    assertThrows(IllegalStateException.class,
            () -> crypto.decrypt(ciphertext.substring(0, ciphertext.length() - 1) + "x"));
    assertFalse(new StorageConfigCrypto("").hasKey());
}
```

Add `private String base64Key()` to the test class; it returns
`Base64.getEncoder().encodeToString(new byte[32])` and keeps key material test-only.

- [ ] **Step 2: Run the focused test and verify the expected RED failure.**

Run: `mvn -Dtest=StorageConfigCryptoTest test`
Expected: compilation/test failure because `StorageConfigCrypto` and the new usage/snapshot types do not exist.

- [ ] **Step 3: Implement the minimal primitives.** Inject `storage.config-encryption-key` into `StorageConfigCrypto` with a blank-safe default; decode exactly 32 Base64 bytes; encode a versioned value containing `v1`, a random 12-byte nonce, and AES-GCM ciphertext; reject malformed values without returning provider details. Add the immutable usage record with non-negative count/bytes and optional `Instant latestObjectModified`.

- [ ] **Step 4: Run the focused test and verify GREEN.**

Run: `mvn -Dtest=StorageConfigCryptoTest test`
Expected: the crypto tests pass with zero failures.

- [ ] **Step 5: Commit the primitive boundary.**

```text
git add blog-springboot/src/main/java/com/wzh/blog/config/StorageConfigCrypto.java blog-springboot/src/main/java/com/wzh/blog/media/StorageUsage.java blog-springboot/src/main/java/com/wzh/blog/media/StorageProviderConfigSnapshot.java blog-springboot/src/test/java/com/wzh/blog/config/StorageConfigCryptoTest.java
git commit -m "feat: add managed storage crypto primitives"
```

### Task 2: Evolve the Flyway schema and profile mapper

**Files:**
- Create: `blog-springboot/src/main/resources/db/migration/V21__managed_storage_profiles.sql`
- Create: `blog-springboot/src/main/java/com/wzh/blog/dao/StorageBootstrapStateDao.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/entity/StorageBootstrapState.java`
- Create: `blog-springboot/src/main/resources/mapper/StorageBootstrapStateDao.xml`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/entity/StorageProviderConfig.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/dao/StorageProviderConfigDao.java`
- Modify: `blog-springboot/src/main/resources/mapper/StorageProviderConfigDao.xml`
- Modify: `blog-springboot/src/test/java/com/wzh/blog/config/DatabaseMigrationIntegrationTest.java`

**Interfaces:**
- Produces `StorageProviderConfigDao.selectAll()`, `selectById(Long)`, `selectByIdForUpdate(Long)`, `selectActive()`, `insertProfile(StorageProviderConfig)`, `updateProfile(StorageProviderConfig)`, `upsertLegacyProfile(StorageProviderConfig)`, `updateValidation(Long, String, LocalDateTime, String)`, `updateUsage(Long, String, Long, Long, LocalDateTime, LocalDateTime, String)`, `activateOnly(Long, Integer, LocalDateTime)`, `countByProvider(String)`, and `countAssetReferences(Long)`.
- Produces `StorageBootstrapStateDao.selectForUpdate()` and `markCompleted(LocalDateTime)`.
- `StorageBootstrapStateDao.selectForUpdate()` returns `StorageBootstrapState`, whose fields are `Integer id`, `Boolean legacyImportCompleted`, `String legacyActiveProvider`, and `LocalDateTime completedAt`.
- Entity properties use `Long id`, `String configName`, `String provider`, `String endpoint`, `String bucket`, `String region`, `String localRoot`, `String publicUrl`, both ciphertext fields, `Boolean active`, source/validation/usage fields, and audit timestamps.

- [ ] **Step 1: Add migration contract assertions before changing the schema.** Extend `DatabaseMigrationIntegrationTest` with assertions that fresh and legacy migrations produce multiple-profile columns, `tb_storage_bootstrap_state`, `storage_config_id` on both asset tables, and exactly one seeded active local row.

```java
assertThat(queryInt(connection,
        "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'tb_storage_provider_config' "
                + "AND column_name IN ('config_name', 'provider', 'is_active', 'usage_bytes')"))
        .isEqualTo(4);
assertThat(queryInt(connection,
        "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'tb_storage_bootstrap_state'"))
        .isEqualTo(1);
assertThat(queryInt(connection,
        "SELECT COUNT(*) FROM tb_storage_provider_config WHERE is_active = 1"))
        .isEqualTo(1);
assertThat(queryInt(connection,
        "SELECT COUNT(*) FROM tb_resource WHERE url LIKE '/admin/storage/configs%'"))
        .isGreaterThanOrEqualTo(5);
```

- [ ] **Step 2: Run the migration test and verify RED.**

Run: `mvn -Dtest=DatabaseMigrationIntegrationTest test`
Expected: the new assertions fail because V21 and the new columns/state table do not exist.

- [ ] **Step 3: Implement V21.** Create the singleton bootstrap-state table first, copy the existing singleton row’s `active_provider` into `legacy_active_provider`, and only then alter the provider table. Drop the old singleton check, add profile, encrypted credential, validation, usage, source, and audit columns, derive a nullable active marker with a unique index, convert the existing row into the seeded local/default profile, and make `id` auto-increment `BIGINT`. Initialize the bootstrap row with `legacy_import_completed = 0` and the copied provider hint (or `NULL` when the old row is unavailable). Add nullable `storage_config_id` indexes and restrictive foreign keys to `tb_content_asset` and `tb_media_asset`; replace the content uniqueness key with `(storage_config_id, object_key)` while retaining `provider` for legacy records. Insert the new storage CRUD/validate/activate/usage resources with `INSERT IGNORE` in the same migration so no applied Flyway migration needs to be edited later.

```sql
ALTER TABLE tb_storage_provider_config
    DROP CHECK ck_storage_provider_config_singleton,
    ADD COLUMN config_name varchar(100) NOT NULL DEFAULT '本地默认配置',
    ADD COLUMN provider varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'local',
    ADD COLUMN endpoint varchar(512) NULL,
    ADD COLUMN bucket varchar(255) NULL,
    ADD COLUMN region varchar(128) NULL,
    ADD COLUMN local_root varchar(1024) NULL,
    ADD COLUMN public_url varchar(1024) NOT NULL DEFAULT '/uploads/',
    ADD COLUMN access_key_id_ciphertext text NULL,
    ADD COLUMN access_key_secret_ciphertext text NULL,
    ADD COLUMN is_active tinyint NOT NULL DEFAULT 0,
    ADD COLUMN config_source varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN last_validation_status varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'NEVER',
    ADD COLUMN last_validation_at datetime NULL,
    ADD COLUMN last_validation_message varchar(1000) NULL,
    ADD COLUMN usage_status varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'NEVER',
    ADD COLUMN usage_object_count bigint NULL,
    ADD COLUMN usage_bytes bigint NULL,
    ADD COLUMN usage_last_modified datetime NULL,
    ADD COLUMN usage_checked_at datetime NULL,
    ADD COLUMN usage_error varchar(1000) NULL,
    ADD COLUMN active_marker tinyint GENERATED ALWAYS AS
        (CASE WHEN is_active = 1 THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_storage_provider_config_active (active_marker);
```

The migration must use explicit ordered statements: create `tb_storage_bootstrap_state` without the foreign key to the profile table, insert its `id=1` row, update its hint from `tb_storage_provider_config.active_provider`, alter the provider table, set the migrated row to the local/default profile and active, then remove the obsolete column and convert the ID. Apply asset columns and foreign keys only after the profile table is valid. This ordering preserves the legacy active-provider hint even though the old singleton column is removed.

- [ ] **Step 4: Implement the entity and mapper.** Map every new column with the project’s underscore-to-camel convention. Use explicit SQL for conditional updates so `activateOnly` first clears active rows and then marks one target inside the caller transaction; update validation/usage snapshots without selecting ciphertext into response types.

- [ ] **Step 5: Run fresh and legacy migration tests and verify GREEN.**

Run: `mvn -Dtest=DatabaseMigrationIntegrationTest test`
Expected: fresh schema, phase-one contract, and legacy upgrade tests pass.

- [ ] **Step 6: Commit the schema and mapper boundary.**

```text
git add blog-springboot/src/main/resources/db/migration/V21__managed_storage_profiles.sql blog-springboot/src/main/java/com/wzh/blog/dao/StorageBootstrapStateDao.java blog-springboot/src/main/java/com/wzh/blog/entity/StorageBootstrapState.java blog-springboot/src/main/resources/mapper/StorageBootstrapStateDao.xml blog-springboot/src/main/java/com/wzh/blog/entity/StorageProviderConfig.java blog-springboot/src/main/java/com/wzh/blog/dao/StorageProviderConfigDao.java blog-springboot/src/main/resources/mapper/StorageProviderConfigDao.xml blog-springboot/src/test/java/com/wzh/blog/config/DatabaseMigrationIntegrationTest.java
git commit -m "feat: add managed storage profile schema"
```

### Task 3: Make provider adapters profile-driven and add real usage aggregation

**Files:**
- Create: `blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/StorageProviderFactory.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/media/StorageProvider.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/media/StorageProviderRegistry.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/config/StorageProviderConfiguration.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/LocalStorageProvider.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/OssStorageProvider.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/CosStorageProvider.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/TosStorageProvider.java`
- Modify: `blog-springboot/src/test/java/com/wzh/blog/media/StorageProviderRegistryTest.java`
- Modify: `blog-springboot/src/test/java/com/wzh/blog/infrastructure/storage/LocalStorageProviderTest.java`
- Create: `blog-springboot/src/test/java/com/wzh/blog/infrastructure/storage/StorageUsageContractTest.java`

**Interfaces:**
- `StorageProvider` gains `StorageUsage usage() throws IOException` and a default no-op `close()`; adapters override `close()` to shut down clients.
- `StorageProviderFactory.create(StorageProviderConfigSnapshot snapshot)` creates exactly one provider adapter without network I/O.
- `StorageProviderRegistry` exposes `providerForNewAsset()`, `providerForConfig(Long)`, `providerForLegacyProvider(StorageProviderType)`, `activeConfig()`, `activeConfigId()`, `publicReference(Long, String)`, `publicBase(Long)`, `invalidate(Long)`, and `refresh(Long)`.

- [ ] **Step 1: Write failing registry and usage tests.** Replace the old type-only registry test with a fake config DAO/factory test that creates two COS snapshots, proves the active profile is selected by ID, proves switching IDs leaves the old provider retrievable, and proves construction performs zero provider operations. Add usage tests for local files and fake paginated summaries.

```java
@Test
void routesNewAndHistoricalObjectsByConfigId() {
    StorageProvider oldCos = fakeProvider(StorageProviderType.COS);
    StorageProvider newCos = fakeProvider(StorageProviderType.COS);
    StorageProviderRegistry registry = registryWithActive(11L, oldCos, newCos);

    assertSame(oldCos, registry.providerForNewAsset());
    registry.refresh(12L);
    assertSame(newCos, registry.providerForNewAsset());
    assertSame(oldCos, registry.providerForConfig(11L));
}
```

- [ ] **Step 2: Run the focused tests and verify RED.**

Run: `mvn -Dtest=StorageProviderRegistryTest,LocalStorageProviderTest,StorageUsageContractTest test`
Expected: compilation/test failures for the missing config-ID and usage methods.

- [ ] **Step 3: Implement the factory and registry.** Remove the fixed four-provider bean collection. Load active/profile rows from the DAO, decrypt credentials only for a provider creation, cache providers by profile ID, resolve legacy provider-only assets only when exactly one configured profile exists, and close invalidated providers. Do not read the environment active provider for runtime selection.

- [ ] **Step 4: Implement local usage.** Walk the normalized local root, count regular files, sum sizes with overflow-safe accumulation, and report the maximum last-modified time; preserve existing object-key safety and upload behavior.

- [ ] **Step 5: Implement cloud usage and profile-aware constructors.**

Use the SDK page contracts already present in the pinned dependencies:

- OSS: `ListObjectsRequest`, marker pagination, `ObjectListing.getObjectSummaries()`, `OSSObjectSummary.getSize()`, `isTruncated()`, and `getNextMarker()`.
- COS: `ListObjectsRequest`, marker pagination, `ObjectListing.getObjectSummaries()`, `COSObjectSummary.getSize()`, `isTruncated()`, and `getNextMarker()`.
- TOS: `ListObjectsType2Input` continuation tokens, `ListObjectsType2Output.getContents()`, `ListedObjectV2` size/last-modified fields, and `getNextContinuationToken()`.

Each adapter must honor the profile’s endpoint/region/bucket, bound page size to 1000 objects, stop after 10,000 pages or 30 seconds, and throw a sanitized `IOException` on failure. Configure SDK clients with the existing short connect/read timeouts; no client is built until put/get/head/delete/exists/validate/usage is called.

- [ ] **Step 6: Run the focused tests and verify GREEN.**

Run: `mvn -Dtest=StorageProviderRegistryTest,LocalStorageProviderTest,StorageUsageContractTest test`
Expected: all focused provider/registry/usage tests pass.

- [ ] **Step 7: Commit provider profile routing.**

```text
git add blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/StorageProviderFactory.java blog-springboot/src/main/java/com/wzh/blog/media/StorageProvider.java blog-springboot/src/main/java/com/wzh/blog/media/StorageProviderRegistry.java blog-springboot/src/main/java/com/wzh/blog/config/StorageProviderConfiguration.java blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/LocalStorageProvider.java blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/OssStorageProvider.java blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/CosStorageProvider.java blog-springboot/src/main/java/com/wzh/blog/infrastructure/storage/TosStorageProvider.java blog-springboot/src/test/java/com/wzh/blog/media/StorageProviderRegistryTest.java blog-springboot/src/test/java/com/wzh/blog/infrastructure/storage/LocalStorageProviderTest.java blog-springboot/src/test/java/com/wzh/blog/infrastructure/storage/StorageUsageContractTest.java
git commit -m "feat: route storage providers by managed profile"
```

### Task 4: Persist profile IDs throughout content and media lifecycles

**Files:**
- Modify: `blog-springboot/src/main/java/com/wzh/blog/content/ContentAsset.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/dao/ContentAssetDao.java`
- Modify: `blog-springboot/src/main/resources/mapper/ContentAssetDao.xml`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/content/ContentAssetPersistenceService.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/content/ArticleContentServiceImpl.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/jobs/ContentAssetCleanupHandler.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/media/MediaAssetLedger.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/infrastructure/media/DatabaseMediaAssetLedger.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/strategy/context/UploadStrategyContext.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/media/AssetReconciliationService.java`
- Modify: `blog-springboot/src/test/java/com/wzh/blog/strategy/context/UploadStrategyContextTest.java`
- Create: `blog-springboot/src/test/java/com/wzh/blog/media/ProfileAwareAssetRoutingTest.java`

**Interfaces:**
- `ContentAsset` gains `Long storageConfigId`; `reserve(...)` accepts the active ID; `openAsset` calls `providerForConfig(id)` and only legacy-null rows use provider fallback.
- `MediaAssetLedger.MediaAssetLocation` becomes `(Long storageConfigId, String provider, String objectKey)`; add `register(String reference, String objectKey, String provider, Long storageConfigId)` while keeping a deprecated three-argument default for old test callers.
- Database ledger queries and cleanup records return/store `storage_config_id`.

- [ ] **Step 1: Write the failing profile-routing regression test.** Use two fake COS providers and two profile IDs; upload one media asset/content version under the first ID, switch the registry to the second ID, then assert open/delete for the old asset still reaches the first provider while a new upload reaches the second.

```java
@Test
void historicalAssetUsesItsOriginalProfileAfterActiveSwitch() {
    StorageProvider oldProvider = mock(StorageProvider.class);
    StorageProvider newProvider = mock(StorageProvider.class);
    when(oldProvider.type()).thenReturn(StorageProviderType.COS);
    when(newProvider.type()).thenReturn(StorageProviderType.COS);
    when(oldProvider.get("articles/1/1-old.md"))
            .thenReturn(storageObject("old content"));

    ProfileRoutingFixture fixture = new ProfileRoutingFixture(oldProvider, newProvider);
    fixture.registry().refresh(12L);

    try (StorageObject object = fixture.openContentAsset(11L, "articles/1/1-old.md")) {
        assertEquals("old content", new String(object.content().readAllBytes(), StandardCharsets.UTF_8));
    }
    verify(oldProvider).get("articles/1/1-old.md");
    verify(newProvider, never()).get("articles/1/1-old.md");
}
```

Define `storageObject(String content)` with
`byte[] bytes = content.getBytes(StandardCharsets.UTF_8); return new StorageObject(
new ByteArrayInputStream(bytes), new StorageObjectMetadata("articles/1/1-old.md",
"text/plain", bytes.length, "checksum", Instant.now()))`,
plus `ProfileRoutingFixture(StorageProvider oldProvider, StorageProvider newProvider)`,
`registry()`, and `openContentAsset(Long storageConfigId, String objectKey)` inside the test
class; the fixture maps IDs 11 and 12 to the two fake providers and calls the production
profile-aware content routing path. The test must assert returned content as well as provider
selection, not merely mock invocation counts.

- [ ] **Step 2: Run the focused routing tests and verify RED.**

Run: `mvn -Dtest=ProfileAwareAssetRoutingTest,UploadStrategyContextTest test`
Expected: compilation/test failure because asset models and routing still accept provider type only.

- [ ] **Step 3: Update content persistence and SQL.** Include `storage_config_id` in all content asset selects/inserts; reserve with both the active profile ID and provider code; keep provider code for audit and legacy fallback; update cleanup handler to resolve by profile ID.

- [ ] **Step 4: Update media ledger and upload context.** On upload capture `registry.activeConfigId()`, build the public reference with that ID, and register both provider and profile ID. On exists/delete use the ledger’s profile ID before reconstructing a legacy reference.

- [ ] **Step 5: Run focused routing and existing content tests and verify GREEN.**

Run: `mvn -Dtest=ProfileAwareAssetRoutingTest,UploadStrategyContextTest,AssetLifecycleServiceTest test`
Expected: profile-aware routing and all selected existing lifecycle tests pass.

- [ ] **Step 6: Commit profile-aware asset routing.**

```text
git add blog-springboot/src/main/java/com/wzh/blog/content/ContentAsset.java blog-springboot/src/main/java/com/wzh/blog/dao/ContentAssetDao.java blog-springboot/src/main/resources/mapper/ContentAssetDao.xml blog-springboot/src/main/java/com/wzh/blog/content/ContentAssetPersistenceService.java blog-springboot/src/main/java/com/wzh/blog/content/ArticleContentServiceImpl.java blog-springboot/src/main/java/com/wzh/blog/jobs/ContentAssetCleanupHandler.java blog-springboot/src/main/java/com/wzh/blog/media/MediaAssetLedger.java blog-springboot/src/main/java/com/wzh/blog/infrastructure/media/DatabaseMediaAssetLedger.java blog-springboot/src/main/java/com/wzh/blog/strategy/context/UploadStrategyContext.java blog-springboot/src/main/java/com/wzh/blog/media/AssetReconciliationService.java blog-springboot/src/test/java/com/wzh/blog/strategy/context/UploadStrategyContextTest.java blog-springboot/src/test/java/com/wzh/blog/media/ProfileAwareAssetRoutingTest.java
git commit -m "feat: bind assets to storage profiles"
```

### Task 5: Implement admin profile validation, CRUD, activation, and usage snapshots

**Files:**
- Create: `blog-springboot/src/main/java/com/wzh/blog/administration/StorageConfigAdminService.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigRequest.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageUsageVO.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageValidationVO.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigSummaryVO.java`
- Create: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigListResponse.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/controller/StorageProviderController.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageProviderSelectionResponse.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageProviderStatusVO.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/vo/StorageProviderValidationVO.java`
- Create: `blog-springboot/src/test/java/com/wzh/blog/administration/StorageConfigAdminServiceTest.java`
- Create: `blog-springboot/src/test/java/com/wzh/blog/controller/StorageProviderControllerTest.java`

**Interfaces:**
- `StorageConfigAdminService.list()` returns `StorageConfigListResponse(activeConfigId, List<StorageConfigSummaryVO>)`.
- `create(StorageConfigRequest, Integer)`, `update(Long, StorageConfigRequest, Integer)`, `delete(Long)`, `validate(Long)`, `activate(Long, Integer)`, and `refreshUsage(Long)` return a safe summary or safe operation result.
- `StorageConfigRequest` is ordered as `name`, `provider`, `endpoint`, `region`, `bucket`, `localRoot`, `publicUrl`, `accessKeyId`, and `accessKeySecret`; conditional validation is performed by the service.
- `StorageConfigSummaryVO` includes ID/name/provider/active/configured/credentialsConfigured, non-secret connection metadata, validation snapshot, and usage snapshot; it contains no credential field.

- [ ] **Step 1: Write failing service tests.** Cover local/cloud conditional fields, create without network I/O, blank credential preservation during update, validation success/failure persistence, activation failure preserving the old active row, successful single-active switching, usage success persistence, usage failure retaining prior values, active deletion rejection, and referenced-profile deletion rejection.

```java
@Test
void blankCredentialsKeepTheExistingCiphertextOnUpdate() {
    StorageProviderConfig existing = new StorageProviderConfig();
    existing.setId(11L);
    existing.setProvider("cos");
    existing.setAccessKeyIdCiphertext("cipher-id");
    existing.setAccessKeySecretCiphertext("cipher-secret");
    when(configDao.selectByIdForUpdate(11L)).thenReturn(existing);
    ArgumentCaptor<StorageProviderConfig> saved = ArgumentCaptor.forClass(StorageProviderConfig.class);

    service.update(11L, new StorageConfigRequest(
            "主 COS", "cos", "https://cos.example.invalid", "ap-shanghai",
            "blog-assets", null, "https://cdn.example.invalid/", "", ""), 7);

    verify(configDao).updateProfile(saved.capture());
    assertEquals("cipher-id", saved.getValue().getAccessKeyIdCiphertext());
    assertEquals("cipher-secret", saved.getValue().getAccessKeySecretCiphertext());
}

@Test
void failedActivationDoesNotChangeTheActiveProfile() {
    StorageProviderConfig target = new StorageProviderConfig();
    target.setId(12L);
    target.setProvider("cos");
    when(configDao.selectByIdForUpdate(12L)).thenReturn(target);
    StorageProvider failing = mock(StorageProvider.class);
    doThrow(new IllegalStateException("unavailable")).when(failing).validateConnection();
    when(factory.create(any())).thenReturn(failing);

    assertThrows(ConflictException.class, () -> service.activate(12L, 7));
    verify(configDao, never()).activateOnly(anyLong(), anyInt(), any());
}
```

- [ ] **Step 2: Run the focused service tests and verify RED.**

Run: `mvn -Dtest=StorageConfigAdminServiceTest,StorageProviderControllerTest test`
Expected: compilation/test failure because the admin service, new VOs, and new controller routes do not exist.

- [ ] **Step 3: Implement request validation and safe mapping.** Normalize provider names using `StorageProviderType.from`, require the local/cloud field sets from the spec, validate HTTP(S) URLs without credentials/query/fragment, normalize local root, encrypt supplied credentials, and map entity rows to summaries without copying ciphertext.

- [ ] **Step 4: Implement validation and usage operations.** Build a provider from the target snapshot, execute a unique `.health/admin/<UUID>.txt` write/read/delete check, update the validation snapshot with safe messages, and call `provider.usage()` for manual usage refresh. On usage failure update only status/error/checked time and retain object count/bytes/latest modified values.

- [ ] **Step 5: Implement activation and deletion transactions.** Validate the target before the DB transaction; lock the target/active rows, use `activateOnly`, update operator/time, and call `registry.refresh(id)` after commit. Reject active deletion and rely on the restrictive FK for referenced assets, translating the database conflict into `ConflictException`.

- [ ] **Step 6: Add new controller routes and compatibility behavior.** Add the seven `/admin/storage/configs` routes with existing admin authorization and `@AccessLimit` on validation, activation, and usage. Keep `/admin/storage/provider` and `/admin/storage/providers` as deprecated adapters: aggregate profiles by provider for old reads, and allow old provider-only switching only when exactly one usable profile exists; otherwise return 409.

- [ ] **Step 7: Run focused tests and verify GREEN.**

Run: `mvn -Dtest=StorageConfigAdminServiceTest,StorageProviderControllerTest test`
Expected: all profile lifecycle, error, compatibility, and response-redaction tests pass.

- [ ] **Step 8: Commit the admin backend contract.**

```text
git add blog-springboot/src/main/java/com/wzh/blog/administration/StorageConfigAdminService.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigRequest.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageUsageVO.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageValidationVO.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigSummaryVO.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfigListResponse.java blog-springboot/src/main/java/com/wzh/blog/controller/StorageProviderController.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageProviderSelectionResponse.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageProviderStatusVO.java blog-springboot/src/main/java/com/wzh/blog/vo/StorageProviderValidationVO.java blog-springboot/src/test/java/com/wzh/blog/administration/StorageConfigAdminServiceTest.java blog-springboot/src/test/java/com/wzh/blog/controller/StorageProviderControllerTest.java
git commit -m "feat: expose managed storage profile admin API"
```

### Task 6: Add one-time legacy import and deployment validation

**Files:**
- Create: `blog-springboot/src/main/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunner.java`
- Create: `blog-springboot/src/test/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunnerTest.java`
- Create: `blog-springboot/src/test/java/com/wzh/blog/config/DeploymentConfigurationValidatorTest.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/config/DeploymentConfigurationValidator.java`
- Modify: `blog-springboot/src/main/java/com/wzh/blog/config/StorageProperties.java`
- Modify: `blog-springboot/src/main/resources/application.yml`
- Modify: `blog-springboot/src/main/resources/application-local.example.yml`
- Modify: `.env.example`

**Interfaces:**
- `StorageConfigBootstrapRunner` implements `ApplicationRunner` and depends on the bootstrap DAO, profile DAO, crypto, legacy `StorageProperties`, and `JdbcTemplate`/asset update operations.
- The runner calls the exact DAO operation `upsertLegacyProfile(StorageProviderConfig)` defined in Task 2, and calls `StorageBootstrapStateDao.markCompleted(LocalDateTime)` after the import/backfill transaction.
- It imports legacy configurations only while `legacy_import_completed = 0`, chooses the old active provider when usable, falls back to seeded local otherwise, backfills asset IDs when provider-to-profile mapping is unique, and marks completion in one transaction.

- [ ] **Step 1: Write failing bootstrap tests.** Cover import of configured local/COS/OSS/TOS values, encrypted persistence, old active selection, local fallback when the old active cloud config is incomplete, no second import after completion, and asset backfill when one profile exists for a provider.

```java
@Test
void importsLegacyEnvironmentOnlyOnceAndEncryptsCloudCredentials() {
    StorageProperties legacy = configuredLegacyProperties();
    when(stateDao.selectForUpdate()).thenReturn(unfinishedState());
    ArgumentCaptor<StorageProviderConfig> imported =
            ArgumentCaptor.forClass(StorageProviderConfig.class);

    runner.run(new DefaultApplicationArguments());
    runner.run(new DefaultApplicationArguments());

    verify(configDao, times(1)).upsertLegacyProfile(imported.capture());
    assertNotEquals("legacy-secret", imported.getValue().getAccessKeySecretCiphertext());
    verify(stateDao).markCompleted(any());
}
```

- [ ] **Step 2: Run the focused bootstrap tests and verify RED.**

Run: `mvn -Dtest=StorageConfigBootstrapRunnerTest test`
Expected: compilation/test failure because the runner and state behavior do not exist.

- [ ] **Step 3: Implement deterministic bootstrap.** Use a locked bootstrap row, import only configured legacy values, preserve the old active-provider hint, reset an unusable cloud active profile to the seeded local profile, update legacy asset rows only when exactly one configured profile matches their provider, and mark completion even when no legacy cloud values exist. Never log imported values.

- [ ] **Step 4: Update deployment validation.** Stop requiring the environment-selected provider’s fields. Validate the DB-selected active profile through the catalog; require `STORAGE_CONFIG_ENCRYPTION_KEY` when a cloud profile exists or is active, retain local-only startup without a key, and keep production origin/monitoring/cursor checks unchanged.

- [ ] **Step 5: Run the focused tests and verify GREEN.**

Run: `mvn -Dtest=StorageConfigBootstrapRunnerTest,DeploymentConfigurationValidatorTest test`
Expected: bootstrap and database-selected-profile deployment validation tests pass.

- [ ] **Step 6: Update environment examples without adding secrets.** Add only the empty `STORAGE_CONFIG_ENCRYPTION_KEY` mapping and deprecation comments for old storage variables; do not add real endpoints or credentials. Keep legacy mappings available for the one-time importer.

- [ ] **Step 7: Commit bootstrap compatibility.**

```text
git add blog-springboot/src/main/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunner.java blog-springboot/src/test/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunnerTest.java blog-springboot/src/main/java/com/wzh/blog/config/DeploymentConfigurationValidator.java blog-springboot/src/main/java/com/wzh/blog/config/StorageProperties.java blog-springboot/src/main/resources/application.yml blog-springboot/src/main/resources/application-local.example.yml .env.example
git commit -m "feat: bootstrap managed storage from legacy settings"
```

### Task 7: Build the administrator storage profile panel

**Files:**
- Create: `blog-vue/admin/src/views/setting/StorageConfigPanel.vue`
- Create: `blog-vue/admin/src/views/setting/StorageConfigPanel.test.js`
- Modify: `blog-vue/shared/api/createApi.js`
- Modify: `blog-vue/admin/src/views/setting/Setting.vue`

**Interfaces:**
- Shared API methods are `storageConfigs()`, `createStorageConfig(payload)`, `updateStorageConfig(id, payload)`, `deleteStorageConfig(id)`, `validateStorageConfig(id)`, `activateStorageConfig(id)`, and `refreshStorageUsage(id)`.
- `StorageConfigPanel` remains an Options API component with `configs`, `activeConfigId`, `dialogVisible`, `editingId`, `form`, `loading`, `saving`, `validatingId`, `activatingId`, `usageRefreshingId`, and `error` state.

- [ ] **Step 1: Write failing component tests.** Test that local selection shows root-directory fields and hides cloud fields, cloud selection shows endpoint/region/bucket/credential fields, blank credential inputs are omitted or sent blank on update, successful activation reloads active state, usage refresh renders object count/bytes/timestamp, and rendered DOM/state never displays response credential properties.

```js
it("shows source-specific fields without echoing credentials", async () => {
  const wrapper = mount(StorageConfigPanel, { global: { mocks: { $api: api } } });

  await wrapper.find("button[data-test='add-storage-config']").trigger("click");
  expect(wrapper.find("[data-test='local-root']").exists()).toBe(true);
  expect(wrapper.find("[data-test='bucket']").exists()).toBe(false);

  await wrapper.find("[data-test='provider']").setValue("cos");
  expect(wrapper.find("[data-test='bucket']").exists()).toBe(true);
  expect(wrapper.text()).not.toContain("accessKeySecret");
});
```

- [ ] **Step 2: Run the focused admin test and verify RED.**

Run: `npm run test:run -- src/views/setting/StorageConfigPanel.test.js`
Expected: failure because the panel and API methods do not exist.

- [ ] **Step 3: Add the shared API methods.** Keep every URL relative to `/api`, use the existing result unwrap convention, and use POST for validate/activate/usage operations.

- [ ] **Step 4: Implement the panel and integrate it into Setting.vue.** Replace the old provider radio/table with the panel; use Element Plus form/table/dialog components, conditionally render fields, keep credential inputs blank on edit, confirm deletion, disable delete for active rows, and format bytes/dates in display-only helpers. Do not copy raw response objects containing unknown credential keys into form state.

- [ ] **Step 5: Run focused tests and verify GREEN.**

Run: `npm run test:run -- src/views/setting/StorageConfigPanel.test.js`
Expected: all panel/API interaction tests pass.

- [ ] **Step 6: Run admin lint and build.**

Run: `npm run lint` and `npm run build` from `blog-vue/admin`.
Expected: exit code 0 with no lint errors or build failures.

- [ ] **Step 7: Commit the admin UI.**

```text
git add blog-vue/admin/src/views/setting/StorageConfigPanel.vue blog-vue/admin/src/views/setting/StorageConfigPanel.test.js blog-vue/shared/api/createApi.js blog-vue/admin/src/views/setting/Setting.vue
git commit -m "feat: add managed storage profile panel"
```

### Task 8: Complete permissions, contracts, and documentation

**Files:**
- Modify: `docs/API-CONTRACT.md`
- Modify: `docs/CONFIGURATION.md`
- Modify: `docs/MEDIA-LIFECYCLE.md`
- Modify: `docs/OPERATIONS-RUNBOOK.md`
- Modify: `docs/DEPLOYMENT-CONTRACT.md`
- Modify: `README.md`

**Interfaces:**
- V21 adds permission resources for `/admin/storage/configs`, `/admin/storage/configs/*`, `/admin/storage/configs/*/validate`, `/admin/storage/configs/*/activate`, and `/admin/storage/configs/*/usage` through Flyway data, not controller hardcoding.
- Documents the response shape, one-time import, encryption-key requirement, manual usage refresh, deletion restrictions, and profile-ID historical routing.

- [ ] **Step 1: Review the documentation requirements against the spec.** Confirm that the API contract lists all seven methods, no-credential response rules, and compatibility behavior for multiple profiles; confirm the configuration and runbook list `STORAGE_CONFIG_ENCRYPTION_KEY`, one-time import, manual usage refresh, last-good snapshots, and deletion restrictions.

- [ ] **Step 2: Run migration and route verification before editing docs.**

Run: `mvn -Dtest=DatabaseMigrationIntegrationTest,MenuRouteContractTest test`
Expected: V21 resource rows and the existing menu route contract pass before documentation changes.

- [ ] **Step 3: Update documentation.** Keep existing old storage resources for rolling compatibility, update configuration tables to mark old provider variables as import-only, document `STORAGE_CONFIG_ENCRYPTION_KEY`, and describe usage failure/last-good snapshot behavior.

- [ ] **Step 4: Verify the documentation contract.**

Run: `rg -n "STORAGE_CONFIG_ENCRYPTION_KEY|storage_config_id|storage/configs|usage|last-good|上次成功|一次性|唯一" docs README.md`
Expected: the new storage configuration, historical routing, migration, usage, and encryption-key rules are present without real credentials.

- [ ] **Step 5: Commit contracts and docs.**

```text
git add docs/API-CONTRACT.md docs/CONFIGURATION.md docs/MEDIA-LIFECYCLE.md docs/OPERATIONS-RUNBOOK.md docs/DEPLOYMENT-CONTRACT.md README.md
git commit -m "docs: document managed storage operations"
```

### Task 9: Run full verification and inspect the final diff

**Files:**
- Test: all backend and admin tests; no production files are intentionally introduced in this task.

- [ ] **Step 1: Run the full backend test suite.**

Run: `mvn test` from `blog-springboot`.
Expected: exit code 0 and all tests pass. Docker-backed migration tests may be skipped only when Docker is unavailable; record the skip explicitly.

- [ ] **Step 2: Run the full admin test suite.**

Run: `npm run test:run` from `blog-vue/admin`.
Expected: exit code 0 and all tests pass.

- [ ] **Step 3: Run admin lint and build.**

Run: `npm run lint` and `npm run build` from `blog-vue/admin`.
Expected: exit code 0 with no errors.

- [ ] **Step 4: Run repository architecture and diff checks.**

Run: `git diff --check`, `git diff --stat`, and:

```text
rg -n "STORAGE_CONFIG_ENCRYPTION_KEY|access_key_secret_ciphertext|storage_config_id|/admin/storage/configs" blog-springboot blog-vue docs .env.example
```

Confirm no tracked file contains a credential value, no frontend code imports a cloud SDK, business components use relative API paths, and only task-related files changed beyond the pre-existing reading-orbit worktree edits.

- [ ] **Step 5: Review staged/unstaged state before handoff.**

Run: `git status --short`, `git diff`, and `git diff --cached`. Stage only managed-storage files if an additional fix commit is necessary; never stage the unrelated reading-orbit files.

## Requirement Coverage Checklist

- [ ] local, COS, OSS, and TOS can each be saved as profiles.
- [ ] Multiple profiles of the same source type are supported.
- [ ] The database enforces one active profile.
- [ ] Admin CRUD never returns credentials and supports blank-on-edit preservation.
- [ ] Activation requires real write/read/delete validation.
- [ ] Cloud/local usage shows actual object/file count and bytes with a last-good snapshot.
- [ ] New content/media records persist `storage_config_id`.
- [ ] Historical objects remain routable after active-profile changes.
- [ ] Legacy environment values import once and stop overriding DB state.
- [ ] SDK clients are lazy and all provider adapters implement the shared usage contract.
- [ ] Permissions, API docs, configuration docs, and operational runbook are updated.
- [ ] Backend tests, admin tests, lint, build, migration checks, and diff checks have fresh passing evidence.
