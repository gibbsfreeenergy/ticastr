# Task 9: Legacy active-provider import compatibility fix

## Issue

`STORAGE_ACTIVE_PROVIDER` was documented as a one-time migration input, but
`StorageProperties` no longer bound it and the bootstrap runner only consulted
the `legacy_active_provider` value copied by V21. The environment hint was
therefore ignored during the first import.

## Fix

- Added `StorageProperties.activeProvider` as a migration-only property and
  mapped `storage.active-provider` to `${STORAGE_ACTIVE_PROVIDER:}`. The
  database catalog and registry remain the runtime provider authority.
- During an incomplete bootstrap, a non-blank supported environment value
  (`local`, `cos`, `oss`, or `tos`) is tried first against usable
  `LEGACY_ENV` profiles. A blank/unsupported hint, or a supported hint with no
  usable imported profile, falls back to the V21 `legacy_active_provider`
  hint and then the existing seeded local fallback.
- A completed bootstrap still returns before reading or selecting from legacy
  inputs. The tests change the environment hint between the first and second
  invocation and verify that no second import, activation, catalog read, or
  registry refresh occurs.
- Added an empty, explicitly documented `STORAGE_ACTIVE_PROVIDER` entry to
  `.env.example`; no real credentials or endpoints were added.

## Focused verification

Command:

```text
mvn "-Dtest=StorageConfigBootstrapRunnerTest,DeploymentConfigurationValidatorTest" test
```

Result: 14 tests passed, 0 failures, 0 errors, 0 skipped.

`git diff --check` passed. The full backend/admin suites were not rerun for
this narrow compatibility fix, per the task instruction to retain focused
test evidence.

The unrelated in-progress admin service, storage mapper, and admin UI changes
were left untouched and are not included in this fix commit.
