# Task 5 report: managed storage profile administration API

## TDD evidence

- RED: `mvn "-Dtest=StorageConfigAdminServiceTest,StorageProviderControllerTest" test`
  - Failed at test compilation as expected because `StorageConfigAdminService` and the Task 5 request/response VOs did not yet exist.
- GREEN: `mvn "-Dtest=StorageConfigAdminServiceTest,StorageProviderControllerTest" test`
  - Passed: 10 tests, 0 failures, 0 errors.
- Static check: `git diff --check`
  - Passed.
- Broader verification: `mvn test`
  - All Task 5-focused tests and database migration/DAO tests passed. The suite has one unrelated pre-existing/flaky failure in `LocalStorageProviderTest.reportsRegularFileUsageAcrossNestedDirectories`: the test expects a fixed 2026-09-01T02:03:04Z timestamp while the first fixture file receives the later current filesystem timestamp. This task does not modify that provider or test.

## Review fixes

- RED (review): `mvn "-Dtest=StorageConfigAdminServiceTest,StorageProviderControllerTest" test`
  - Failed at test compilation as expected before the safe provider-failure diagnostic helper existed.
- GREEN (review): `mvn "-Dtest=StorageConfigAdminServiceTest,StorageProviderControllerTest" test`
  - Passed: 13 tests, 0 failures, 0 errors.
- Deprecated provider-only validation/switching now treat every profile of that provider, including drafts, as an ambiguity. A configured profile plus a draft therefore returns a safe conflict.
- Deletion now locks the target and current active row, then checks references and deletes in one transaction. The null-transaction unit-test construction follows the same locked helper and verifies call order.
- Provider validation and usage failures now log only a fixed operation name, configuration ID, and exception type; tests assert both this diagnostic and the returned safe snapshots exclude credentials, ciphertext, authorization data, signatures, and provider URLs.

## Delivered behavior

- Added safe profile list/create/update/delete/validate/activate/usage operations and all seven `/admin/storage/configs` routes.
- Applies local/cloud conditional validation, normalizes absolute local roots, rejects unsafe HTTP(S) URLs, AES-GCM-encrypts supplied cloud credentials, and preserves existing ciphertext when update credentials are blank.
- Validation and usage expose and persist only fixed safe messages; usage failures retain the last successful values.
- Activation validates before a transaction locks the target/current rows and uses `activateOnly`; registry refresh happens after persistence. Active and referenced profiles cannot be deleted.
- Deprecated provider-only reads now aggregate profile availability, and provider-only validation/switching reject non-unique usable profiles with a safe conflict.
- Safe response VOs contain no credential/ciphertext fields. The credential-bearing update request is deliberately excluded from operation logging.

## Changed files

- `blog-springboot/src/main/java/com/wzh/blog/administration/StorageConfigAdminService.java`
- `blog-springboot/src/main/java/com/wzh/blog/controller/StorageProviderController.java`
- `blog-springboot/src/main/java/com/wzh/blog/dao/StorageProviderConfigDao.java`
- `blog-springboot/src/main/resources/mapper/StorageProviderConfigDao.xml`
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageConfig*.java`
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageUsageVO.java`
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageValidationVO.java`
- `blog-springboot/src/main/java/com/wzh/blog/vo/StorageProvider{SelectionResponse,StatusVO,ValidationVO}.java`
- `blog-springboot/src/test/java/com/wzh/blog/administration/StorageConfigAdminServiceTest.java`
- `blog-springboot/src/test/java/com/wzh/blog/controller/StorageProviderControllerTest.java`

## Narrow concern

The project-wide local-provider usage test uses a past fixed timestamp and can fail after that wall-clock time; it is unrelated to this API change and was left untouched.
