# Task 7 Fix Round 2 Report: Allowlisted storage status messages

## Review fixes

- Replaced the status-detail keyword blacklist with exact, field-specific allowlists.
  - Validation accepts only trimmed `验证成功`, `配置验证失败`, or `配置字段不完整`.
  - Usage accepts only trimmed `使用量刷新失败`.
- Every other message, including opaque identifiers, random tokens, URLs, credential-shaped values, ciphertext, and unknown response fields, is stored as an empty detail and is not rendered. The response-to-state and response-to-form field whitelists remain unchanged.
- Retained the previous initial-load-error and refresh-failure list-preservation behavior and its regression test.

## TDD and verification evidence

- RED: `npm run test:run -- src/views/setting/StorageConfigPanel.test.js`
  - Failed as expected because an opaque usage token without a blacklist keyword was retained.
- GREEN: `npm run test:run -- src/views/setting/StorageConfigPanel.test.js`
  - Passed: 1 file, 7 tests, 0 failures.
- Admin suite: `npm run test:run`
  - Passed: 3 files, 12 tests, 0 failures.
- Lint: `npm run lint`
  - Passed (exit 0).
- Build: `npm run build`
  - Passed; Vite built successfully in 982ms.
- Static check: `git diff --check`
  - Passed (exit 0; only CRLF conversion warnings were emitted).

## Changed files

- `blog-vue/admin/src/views/setting/StorageConfigPanel.vue`
- `blog-vue/admin/src/views/setting/StorageConfigPanel.test.js`
- `.superpowers/sdd/2026-08-31-storage-config-management-plan/task-7-fix-round-2-report.md`

## Remaining risk

Future safe server copy must be added deliberately to the matching allowlist. Until then it is omitted while the generic validation or usage status continues to render, which preserves the no-secret display boundary.
