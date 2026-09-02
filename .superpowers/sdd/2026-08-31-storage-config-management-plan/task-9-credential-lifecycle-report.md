# Task 9 credential lifecycle and provider shutdown fix

## Scope

This focused fix addresses the reviewer findings for managed storage provider
credential lifetime, registry shutdown, and ordinary cloud-operation error
sanitization. No real credentials, ciphertext, signed URLs, or tokens are
included in this report.

## Implemented

- `StorageProviderFactory.create(StorageProviderConfig)` now creates a
  non-sensitive profile with an encrypted credential supplier. It does not
  decrypt while creating the adapter or while the adapter is cached.
- OSS, COS, and TOS resolve credentials only inside lazy SDK client creation.
  The resolved values are passed directly to the SDK builder and are not kept
  in provider profile fields. Existing plaintext snapshot constructors remain
  available for legacy/test callers and are explicitly separated from the
  managed path.
- `StorageProviderRegistry.closeAll()` is a Spring shutdown callback. It
  detaches managed and legacy cached providers, clears configuration and active
  references, closes each provider once, tolerates close failures, and is
  idempotent. `invalidate` still closes its removed provider and lifecycle
  locking prevents creation/close races.
- OSS, COS, and TOS `put`, `get`, `head`, `delete`, and `exists` now expose
  cause-free safe `IOException` instances. Provider-response stream close
  failures are sanitized as well. Missing-object delete/exists behavior is
  unchanged. Cloud validation wrappers no longer retain raw causes.

## Focused verification

The focused Maven run completed successfully before the final mechanical
lifecycle-lock placement adjustment:

```text
mvn -Dtest=StorageProviderFactoryTest,StorageProviderRegistryTest,StorageUsageContractTest test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

`git diff --check` completed with exit code 0. The full backend suite and
frontend checks were not run in this handoff because the requester explicitly
asked to stop waiting after the focused verification.

## Remaining coverage note

The focused delayed-decryption test uses the OSS test client seam; COS and TOS
use the same managed snapshot credential boundary in production code but do
not have separate fake-SDK operation tests. No changes were made to
`StorageConfigAdminService`, bootstrap, frontend, or unrelated files.
