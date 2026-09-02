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

## Documentation updated in the initial documentation pass

- `docs/API-CONTRACT.md`
- `docs/CONFIGURATION.md`
- `docs/MEDIA-LIFECYCLE.md`
- `docs/OPERATIONS-RUNBOOK.md`
- `docs/DEPLOYMENT-CONTRACT.md`
- `README.md`

## Contract coverage

- Replaced provider-only runtime wording in the user-facing runtime contract set updated in this round with managed storage profiles backed by `tb_storage_provider_config`.
- Documented all seven `/api/admin/storage/configs` methods and their real request fields:
  `name`, `provider`, `endpoint`, `region`, `bucket`, `localRoot`, `publicUrl`, `accessKeyId`, `accessKeySecret`.
- Recorded provider-conditional validation:
  `local` requires `localRoot` plus `publicUrl`; `cos` / `oss` / `tos` require endpoint, region, bucket, public URL, and credentials; opposite-side request fields are ignored/cleared rather than rejected solely for being present; update-time blank credentials retain existing ciphertext.
- Documented safe response boundaries:
  no credential plaintext, ciphertext, signatures, Authorization, supplier request URLs, or arbitrary object keys in API responses or docs. Safe summary fields may still include validated `endpoint`, `region`, `bucket`, `localRoot`, and `publicUrl`.
- Recorded the implemented status behavior:
  `validate` returns a safe success/failed snapshot, `activate` returns 409 without switching when validation fails, `usage` is manual and keeps last-good count/bytes/latest-modified on failure.
- Documented that provider types are exactly `local`, `cos`, `oss`, `tos`; multiple profiles per provider are valid; database `is_active` is the only active selector and only one row can be active.
- Documented `STORAGE_CONFIG_ENCRYPTION_KEY` and AES-GCM credential storage for cloud profiles.
- Documented one-time legacy bootstrap from old storage environment variables and the rule that completed bootstrap state prevents later overrides.
- Documented content/media persistence of `storage_config_id`, with active switches affecting only new objects and historical assets continuing through the stored profile ID.
- Documented deletion restrictions for active and referenced profiles.
- Kept rolling-compatibility documentation for the old provider routes and their 409 ambiguity rule when one provider maps to multiple profiles.
- Documented that V21 migration owns the storage permission resources; no controller or doc text treats permissions as executable configuration.

## Remaining documentation boundary

- This report does not claim every historical planning/design artifact in the repository was rewritten. Files under `docs/superpowers/specs/` and `docs/superpowers/plans/` remain historical design records unless separately updated; the runtime contract set for operators and maintainers is the user-visible root docs updated in this stage.

## Narrow risk

The runtime docs now match the implemented API behavior, but the repository still contains historical design documents that may describe earlier intent such as broader 503 wording or planning-time architecture language. They are historical context, not live runtime contract. Also, storage troubleshooting still depends on admin snapshots plus generic logs/health because there are no dedicated provider validate/delete/usage Micrometer meters today.
