# Task 1 Report

Status: complete

RED evidence:
- Command: `mvn -Dtest=StorageConfigCryptoTest test`
- Result: test compilation failed as expected because `com.wzh.blog.config.StorageConfigCrypto` did not exist yet.
- Key compiler output:
  - `cannot find symbol: class StorageConfigCrypto`
  - `cannot find symbol: class StorageConfigCryptoTest`

GREEN evidence:
- Command: `mvn -Dtest=StorageConfigCryptoTest test`
- Result: build success.
- Test summary: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

Files changed:
- `blog-springboot/src/main/java/com/wzh/blog/config/StorageConfigCrypto.java`
- `blog-springboot/src/main/java/com/wzh/blog/media/StorageUsage.java`
- `blog-springboot/src/main/java/com/wzh/blog/media/StorageProviderConfigSnapshot.java`
- `blog-springboot/src/test/java/com/wzh/blog/config/StorageConfigCryptoTest.java`

Notes:
- `StorageConfigCrypto` uses AES-GCM with a random 12-byte nonce and a versioned `v1:<nonce>:<ciphertext>` format.
- `StorageProviderConfigSnapshot.toString()` omits credential fields.
- `StorageUsage` rejects negative counts and byte totals.

Concerns:
- Only the task-focused Maven test was run; the broader module test suite was not executed.
- Nonblank but invalid encryption keys are treated as unavailable rather than failing startup, which is acceptable for this primitive boundary but may need stricter validation in a later task.
