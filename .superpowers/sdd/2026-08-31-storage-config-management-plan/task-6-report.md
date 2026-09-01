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

- Added a transactional `StorageConfigBootstrapRunner` that locks the singleton state row, stops immediately after a completed state, imports usable local/COS/OSS/TOS legacy inputs as `LEGACY_ENV` profiles, AES-GCM encrypts cloud credentials, selects the persisted legacy active-provider hint only when usable, and otherwise activates the seeded `DEFAULT` local profile.
- Asset `storage_config_id` backfill runs only when exactly one usable catalog profile matches the legacy provider. It updates content and media rows independently, preserving ambiguous rows as `NULL`.
- Completion is recorded even when no cloud legacy variables are present. Registry refresh occurs after commit, and import makes no provider SDK/network call.
- Deployment validation now uses the database active profile and catalog. Production-like local-only catalogs can run without a key; usable cloud profiles require a valid configured encryption key and complete cloud fields. Existing deployment URL, monitoring, cursor-secret, and search-index checks remain intact.
- Removed the legacy active-provider setting from `StorageProperties` and examples. Legacy storage variables remain clearly marked as one-time bootstrap inputs; database profiles are the runtime authority.

## Changed files

- `blog-springboot/src/main/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunner.java`
- `blog-springboot/src/main/java/com/wzh/blog/config/DeploymentConfigurationValidator.java`
- `blog-springboot/src/main/java/com/wzh/blog/config/StorageProperties.java`
- `blog-springboot/src/main/resources/application.yml`
- `blog-springboot/src/main/resources/application-local.example.yml`
- `.env.example`
- `blog-springboot/src/test/java/com/wzh/blog/bootstrap/StorageConfigBootstrapRunnerTest.java`
- `blog-springboot/src/test/java/com/wzh/blog/config/DeploymentConfigurationValidatorTest.java`

## Narrow concern

The focused tests exercise the locked/bootstrap behavior with DAO and JDBC mocks; they deliberately do not contact cloud SDKs or a live storage endpoint. The existing database migration/DAO integration tests remain responsible for validating the V21 schema against MySQL.
