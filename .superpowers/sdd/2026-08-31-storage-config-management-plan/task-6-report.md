# Task 6 Report: One-time legacy storage import and deployment validation

## TDD evidence

- RED: `mvn "-Dtest=StorageConfigBootstrapRunnerTest,DeploymentConfigurationValidatorTest" test`
  - Failed at test compilation as expected: `StorageConfigBootstrapRunner` did not exist.
- GREEN: `mvn "-Dtest=StorageConfigBootstrapRunnerTest,DeploymentConfigurationValidatorTest" test`
  - Passed: 8 tests, 0 failures, 0 errors, 0 skipped.
- Additional affected-boundary verification: `mvn "-Dtest=StorageConfigBootstrapRunnerTest,DeploymentConfigurationValidatorTest,StorageProviderRegistryTest,StorageConfigAdminServiceTest,StorageProviderControllerTest,StorageConfigCryptoTest" test`
  - Passed: 24 tests, 0 failures, 0 errors, 0 skipped.
- Static check: `git diff --check`
  - Passed (exit 0; Git emitted only CRLF conversion warnings).

## Delivered behavior

- Added a transactional `StorageConfigBootstrapRunner` that locks the singleton state row, stops immediately after a completed state, imports only explicitly supplied usable local/COS/OSS/TOS legacy inputs as `LEGACY_ENV` profiles, AES-GCM encrypts cloud credentials, selects the imported profile matching the persisted legacy active-provider hint only when usable, and otherwise activates the seeded `DEFAULT` local profile.
- Asset `storage_config_id` backfill runs only when exactly one usable catalog profile matches the legacy provider. It updates content and media rows independently, preserving ambiguous rows as `NULL`.
- Completion is recorded even when no cloud legacy variables are present. Registry refresh occurs after commit, and import makes no provider SDK/network call.
- Deployment validation now uses the database active profile and catalog. Production-like local-only catalogs can run without a key; usable cloud profiles require a valid configured encryption key and complete cloud fields. Existing deployment URL, monitoring, cursor-secret, and search-index checks remain intact.
- Removed the legacy active-provider setting from `StorageProperties` and examples. Legacy storage variables remain clearly marked as one-time bootstrap inputs; database profiles are the runtime authority.

## Review fixes

- RED: `mvn "-Dtest=StorageConfigBootstrapRunnerTest,DeploymentConfigurationValidatorTest,ManagedLocalStorageResourceResolverTest" test`
  - Failed at test compilation as expected because `ManagedLocalStorageResourceResolver` did not yet exist.
- GREEN: the same focused command passed: 12 tests, 0 failures, 0 errors, 0 skipped.
- MySQL/Testcontainers verification was available:
  - `DatabaseMigrationIntegrationTest`: 3 passed, 0 failures, 0 errors.
  - `StorageProviderConfigDaoIntegrationTest`: 3 passed, 0 failures, 0 errors.
- `git diff --check` passed.

The review correction removes the `./uploads` and `/uploads/` legacy-input defaults. With no explicit `STORAGE_LOCAL_*` values, no `LEGACY_ENV` local profile is inserted and the seeded `DEFAULT` local profile remains the fallback/backfill target. With an explicit legacy local root, that imported profile is selected for active storage and legacy asset backfill; the seeded fallback is excluded only from this specific local backfill candidate set.

`WebMvcConfig` no longer reads `StorageProperties.localRoot`. Its `/uploads/**` resource resolver queries the current active database profile per request and serves only a real file inside an active local profile's normalized real path. This is request-time work, so it cannot query before Flyway migration; cloud profiles and traversal paths return no resource.

## Changed files

- `blog-springboot/src/main/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunner.java`
- `blog-springboot/src/main/java/com/wzh/blog/config/DeploymentConfigurationValidator.java`
- `blog-springboot/src/main/java/com/wzh/blog/config/StorageProperties.java`
- `blog-springboot/src/main/java/com/wzh/blog/config/WebMvcConfig.java`
- `blog-springboot/src/main/java/com/wzh/blog/config/ManagedLocalStorageResourceResolver.java`
- `blog-springboot/src/main/resources/application.yml`
- `blog-springboot/src/main/resources/application-local.example.yml`
- `.env.example`
- `blog-springboot/src/test/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunnerTest.java`
- `blog-springboot/src/test/java/com/wzh/blog/config/DeploymentConfigurationValidatorTest.java`
- `blog-springboot/src/test/java/com/wzh/blog/config/ManagedLocalStorageResourceResolverTest.java`

## Narrow concern

The request-time local resolver performs one active-profile DAO read for each `/uploads/**` request so active local profile changes take effect without a restart. This is intentional for database authority; production traffic may later warrant a short, invalidation-aware cache if profiling shows it is needed.
