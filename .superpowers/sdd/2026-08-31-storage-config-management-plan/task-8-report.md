# Task 8 Report: Managed storage contracts and operations documentation

## Status

Complete.

## Before / after verification

- Before editing:
  - Command: `mvn "-Dtest=DatabaseMigrationIntegrationTest,MenuRouteContractTest" test`
  - Result: `BUILD SUCCESS`
  - Summary: 5 tests run, 0 failures, 0 errors, 0 skipped.
- After editing:
  - Command: `rg -n "STORAGE_CONFIG_ENCRYPTION_KEY|storage_config_id|storage/configs|usage|last-good|上次成功|一次性|唯一|LEGACY_ENV" docs README.md .env.example`
  - Result: expected contract terms found in the updated docs and existing examples.
  - Command: `git diff --check`
  - Result: exit `0`.
  - Command: `mvn "-Dtest=DatabaseMigrationIntegrationTest,MenuRouteContractTest" test`
  - Result: `BUILD SUCCESS`
  - Summary: 5 tests run, 0 failures, 0 errors, 0 skipped.

## Documentation updated

- `docs/API-CONTRACT.md`
- `docs/CONFIGURATION.md`
- `docs/MEDIA-LIFECYCLE.md`
- `docs/OPERATIONS-RUNBOOK.md`
- `docs/DEPLOYMENT-CONTRACT.md`
- `README.md`

## Contract coverage

- Replaced provider-only runtime wording with managed storage profiles backed by `tb_storage_provider_config`.
- Documented all seven `/api/admin/storage/configs` methods and their real request fields:
  `name`, `provider`, `endpoint`, `region`, `bucket`, `localRoot`, `publicUrl`, `accessKeyId`, `accessKeySecret`.
- Recorded provider-conditional validation:
  `local` requires `localRoot` plus `publicUrl`; `cos` / `oss` / `tos` require endpoint, region, bucket, public URL, and credentials; update-time blank credentials retain existing ciphertext.
- Documented safe response boundaries:
  no credential plaintext, ciphertext, signatures, Authorization, request URLs, or arbitrary object keys in API responses or docs.
- Recorded the implemented status behavior:
  `validate` returns a safe success/failed snapshot, `activate` returns 409 without switching when validation fails, `usage` is manual and keeps last-good count/bytes/latest-modified on failure.
- Documented that provider types are exactly `local`, `cos`, `oss`, `tos`; multiple profiles per provider are valid; database `is_active` is the only active selector and only one row can be active.
- Documented `STORAGE_CONFIG_ENCRYPTION_KEY` and AES-GCM credential storage for cloud profiles.
- Documented one-time legacy bootstrap from old storage environment variables and the rule that completed bootstrap state prevents later overrides.
- Documented content/media persistence of `storage_config_id`, with active switches affecting only new objects and historical assets continuing through the stored profile ID.
- Documented deletion restrictions for active and referenced profiles.
- Kept rolling-compatibility documentation for the old provider routes and their 409 ambiguity rule when one provider maps to multiple profiles.
- Documented that V21 migration owns the storage permission resources; no controller or doc text treats permissions as executable configuration.

## Narrow risk

The docs now match the implemented API behavior, which differs slightly from the earlier design note that mentioned 503 for runtime validation and usage dependency failures. The live implementation returns safe failed snapshots for `validate` and `usage`, and 409 only for failed activation or deletion conflicts.
