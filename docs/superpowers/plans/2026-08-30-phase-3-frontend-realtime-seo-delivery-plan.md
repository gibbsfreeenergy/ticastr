# Phase 3 Frontend, Realtime, SEO, and Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the third-priority product and delivery work: split the large Vue screens without changing the Options API convention, make article editing/versioning and content loading resilient, make single-instance chat reconnectable and leak-free, add static SEO output and responsive images, and enforce the full verification path in CI.

**Architecture:** The public and admin SPAs remain separate Vue 3/Vite applications. blog-vue/shared owns HTTP, API, error, cache, content, and realtime contracts. Public article metadata, content, comments, and recommendations load independently. The admin editor operates on Markdown asset versions. The browser never knows storage-provider credentials or constructs provider URLs. SEO files are generated at build time from the public API and the built shell.

**Tech Stack:** Vue 3 Options API, Vue Router, Vuex, Vuetify, Element Plus, Vite, Vitest, Vue Test Utils, Markdown-It, DOMPurify, Node 24, Playwright for browser smoke, and Sharp as a required build-time dependency for image variants.

**Spec:** [Ticastr single-instance architecture design](../specs/2026-08-30-ticastr-single-instance-design.md)

## Global Constraints

- Phase 1 and Phase 2 contracts are authoritative: article metadata/content are separate endpoints, Redis is optional, and the active storage provider is selected by the backend.
- Preserve Vue Options API, double quotes, existing directory responsibilities, and relative /api/... requests. Do not rewrite the applications into Composition API or move all code into a new framework.
- Work around existing dirty files. Do not reset or delete unrelated user changes. Use apply_patch for source edits.
- Every behavior change follows RED -> GREEN -> REFACTOR. Component tests must fail before implementation and must assert observable loading/error/retry behavior rather than internal implementation details.
- Public pages must remain usable when the content endpoint, WebSocket, image variant, or optional analytics path fails. Show a retryable state instead of an empty successful-looking page.
- Do not put secrets, raw provider credentials, arbitrary object keys, or internal storage errors into browser state, HTML, logs, or SEO files.
- Run lint, unit tests, production builds, bundle budgets, browser smoke, and static SEO checks from fresh commands before claiming completion.
- Do not increase bundle budgets to hide regressions. Split or lazy-load code when a budget fails.

## Dependency Order

1. Stabilize shared API/content/realtime contracts.
2. Split the public article and admin screens around those contracts.
3. Add admin provider/outbox operations and article version workflows.
4. Harden chat behavior and backend protocol compatibility.
5. Generate SEO pages, feeds, sitemap, and responsive image variants.
6. Add browser smoke, performance checks, and delivery gates.

---

## Task 1: Stabilize Shared HTTP, Content, Error, and Realtime Contracts

**Files:**

- Add blog-vue/shared/api/contentApi.js.
- Add blog-vue/shared/api/error.js.
- Add blog-vue/shared/realtime/reconnectPolicy.js.
- Modify blog-vue/shared/api/createApi.js, blog-vue/shared/http/createHttpClient.js, and blog-vue/shared/realtime/createWebSocketClient.js.
- Modify blog-vue/blog/src/api/http.js and blog-vue/admin/src/api/http.js only to consume the shared contracts.
- Add blog-vue/shared/api/contentApi.test.js, blog-vue/shared/http/createHttpClient.test.js, and blog-vue/shared/realtime/createWebSocketClient.test.js.

### RED

- Write tests for these exact behaviors:
  - article.byId(id) requests metadata only; article.content(id) requests /api/articles/{id}/content and returns Markdown plus asset version.
  - Admin content methods use /api/admin/articles/{id}/content and send expectedVersion.
  - A response with ETag stores the validator for the same article asset; the next request sends If-None-Match; a 304 reuses the cached body without showing an error.
  - A 401 emits one authentication-expired event, a 429 exposes retryAfterSeconds, a 404 remains a not-found result, and an offline/network error is retryable.
  - The WebSocket client reconnects with delays 1s, 2s, 4s, 8s, 16s, 30s, never exceeds 30s, stops immediately after an intentional close, and clears heartbeat/reconnect timers on close().
  - Incoming realtime messages with the same eventId are delivered once; malformed messages go to onProtocolError and do not crash the client.
- Run from blog-vue/blog and the shared test location used by the repository:

  ~~~powershell
  npm run test:run -- shared/api/contentApi.test.js shared/http/createHttpClient.test.js shared/realtime/createWebSocketClient.test.js
  ~~~

- Expected RED result: the current API has no dedicated content cache/validator contract and the WebSocket adapter has no automatic retry, event deduplication, or protocol-error callback.

### GREEN

- Define these shared API methods:

  ~~~text
  article.byId(id)
  article.content(id, requestConfig)
  article.adminById(id)
  article.adminContent(id, requestConfig)
  article.saveMetadata(payload)
  article.saveContent(id, payload)
  article.previewContent(id, payload)
  article.versions(id, config)
  article.restoreVersion(id, version)
  storage.providers()
  storage.validate(provider)
  storage.activate(provider)
  outbox.list(config)
  outbox.retry(eventId)
  ~~~

- Keep the low-level Axios methods for legacy screens, but do not add endpoint strings to new components. contentApi.js must normalize the backend envelope and return { data, etag, lastModified, notModified }.
- Add an in-memory content cache keyed by articleId:assetId:version with a bounded size of 50 and TTL of 60 seconds. Cache validators and body separately; 304 is valid only when a body exists.
- Normalize errors to { kind, status, message, retryable, retryAfterSeconds, traceId }. Use generic user-facing messages and keep raw backend details out of rendered HTML.
- Extend createWebSocketClient with connect, close, send, isOpen, isConnecting, getReconnectAttempt, and callbacks onOpen, onMessage, onError, onClose, onProtocolError, onReconnectScheduled. Backoff must be injectable for tests.
- Define the realtime envelope:

  ~~~json
  {
    "eventId": "uuid",
    "type": "CHAT_MESSAGE",
    "serverTime": "2026-08-30T00:00:00Z",
    "data": {}
  }
  ~~~

- Add bounded event-ID deduplication with a 500-entry FIFO/TTL set. The shared client must not parse message bodies into a chat-specific type.
- Run focused Vitest tests until green.

### REFACTOR

- Remove duplicated Axios response/error handling from both app-specific src/api/http.js files and keep URL construction in blog-vue/shared/api/createApi.js.
- Add a small API contract test that scans public/admin source for provider hostnames, access-key names, and hardcoded localhost:8090.
- Document the HTTP validators, normalized errors, WebSocket envelope, reconnect policy, and offline behavior in blog-vue/README.md and docs/API-CONTRACT.md.

## Task 2: Split the Public Article Page and Add Progressive Content Loading

**Files:**

- Add blog-vue/blog/src/views/article/ArticleMeta.vue.
- Add blog-vue/blog/src/views/article/ArticleContent.vue.
- Add blog-vue/blog/src/views/article/ArticleToc.vue.
- Add blog-vue/blog/src/views/article/ArticleComments.vue.
- Add blog-vue/blog/src/views/article/ArticleRecommendations.vue.
- Add blog-vue/blog/src/views/article/ArticleNavigation.vue.
- Add blog-vue/blog/src/views/article/articleViewModel.js.
- Modify blog-vue/blog/src/views/article/Article.vue and its style blocks.
- Add blog-vue/blog/src/views/article/articleViewModel.test.js, ArticleContent.test.js, and ArticlePage.test.js.

### RED

- Write component/view-model tests that assert:
  - Initial route load requests metadata first and renders title/category/timestamps/skeleton before content.
  - Content is requested only after metadata identifies a valid article and is rendered as sanitized Markdown.
  - Comments and recommendations load independently; a recommendation failure does not blank the article body.
  - A content 404, provider read failure, and network failure render distinct not-found/error/retry states.
  - Clicking retry requests content again without duplicating comments, TOC entries, or recommendations.
  - The document title/canonical data updates from article metadata and resets when navigating to another article.
  - A public article with no tags/navigation/reward configuration does not throw during render.
- Run:

  ~~~powershell
  npm run test:run -- src/views/article/articleViewModel.test.js src/views/article/ArticleContent.test.js src/views/article/ArticlePage.test.js
  ~~~

- Expected RED result: the current Article.vue loads and renders a large combined object including article content, comments, recommendations, and navigation.

### GREEN

- Make Article.vue a page coordinator with state { metadata, content, comments, recommendations, loading, contentState, error }. It should not contain Markdown rendering, TOC generation, comment form, or recommendation markup.
- Move the banner/date/category/count display to ArticleMeta.vue. It receives metadata and emits navigation actions; it never calls the API.
- Move Markdown-It plus DOMPurify rendering to ArticleContent.vue. Disable raw HTML in Markdown-It, sanitize the rendered output, preserve safe code highlighting, and show a retry button for failed content.
- Move heading extraction and TOC scroll behavior to ArticleToc.vue. Rebuild the TOC only when the sanitized content changes; remove event listeners on unmount.
- Move comments and recommendation calls to their own components with request cancellation/route-generation checks so a slow previous article cannot overwrite the current route.
- Move previous/next links, tags, copyright, and share actions to ArticleNavigation.vue.
- Use the shared content API and send validators. Keep the browser request order: metadata -> content; comments/recommendations may start after metadata but must not block content.
- Preserve existing CSS class names where the layout depends on them, then move each component's styles into the nearest component or a page stylesheet.
- Run focused tests, then public lint/build.

### REFACTOR

- Reduce Article.vue below 250 lines and keep each extracted component below 300 lines. Do not split purely presentational one-line fragments into dozens of files.
- Add a route-change test that mounts one article, navigates to another, resolves the first request late, and proves stale data is ignored.
- Add loading=lazy and explicit width/height/aspect-ratio to non-critical article images; keep the hero image eager with a responsive srcset.

## Task 3: Implement Admin Markdown Editing, Autosave, Preview, Publish, and Version Restore

**Files:**

- Add blog-vue/admin/src/views/article/ArticleEditorForm.vue.
- Add blog-vue/admin/src/views/article/ArticlePreview.vue.
- Add blog-vue/admin/src/views/article/ArticleVersionDialog.vue.
- Add blog-vue/admin/src/views/article/articleEditorState.js.
- Modify blog-vue/admin/src/views/article/Article.vue and ArticleList.vue.
- Modify blog-vue/admin/src/components/Editor.vue only for explicit Markdown/preview events and cleanup.
- Add blog-vue/admin/src/views/article/articleEditorState.test.js, ArticleEditorForm.test.js, and ArticleVersionDialog.test.js.
- Modify blog-vue/shared/api/createApi.js if any admin content/version method is missing.

### RED

- Write tests that assert:
  - Loading an admin article requests metadata and current Markdown separately, displaying an editor skeleton until the content response arrives.
  - A dirty editor autosaves after 2 seconds of inactivity, coalesces rapid changes into one request, and never saves after unmount.
  - Autosave includes expectedVersion; a version conflict preserves the local draft, shows a conflict action, and does not overwrite server content.
  - Preview renders the same sanitized Markdown pipeline as the public page without publishing.
  - Publish is disabled while a save is in flight, and a failed publish leaves the current draft/version visible with a retry action.
  - The version dialog pages history, restores a selected version only after confirmation, and refreshes the editor with the new version.
- Run:

  ~~~powershell
  npm run test:run -- src/views/article/articleEditorState.test.js src/views/article/ArticleEditorForm.test.js src/views/article/ArticleVersionDialog.test.js
  ~~~

- Expected RED result: the current admin article screen submits one large article payload and has no content-version/autosave/restore state machine.

### GREEN

- Define articleEditorState as an Options-API-compatible state machine with states loading, ready, dirty, saving, conflict, publishing, published, and error. It owns timers and exposes dispose().
- Load adminById(id) then adminContent(id). Keep { metadata, markdown, version, lastSavedAt, dirty, saveError, publishError } separate; never put server secrets or provider information in the editor state.
- Autosave uses a 2-second debounce, one in-flight request, and a saveAgain flag when edits occur during a save. On success replace version and clear dirty; on 409 keep local Markdown and expose server version/content for comparison.
- Preview receives Markdown locally and uses one shared safe renderer. It does not call the content endpoint or mutate the current version.
- Publish calls metadata/status endpoint only after the latest Markdown save succeeds. Show an explicit status and restore the previous editor state after a retryable error.
- Version history calls GET /api/admin/articles/{id}/versions?cursor=..., renders version/date/size/checksum/status, and restores through POST /api/admin/articles/{id}/versions/{version}/restore. The response becomes a new immutable content version; the UI does not overwrite an old object.
- Keep the existing editor upload flow but route image uploads through the shared relative API URL and insert the returned safe asset URL/identifier; never accept a caller-chosen object key.
- Run focused tests, admin lint, and build.

### REFACTOR

- Reduce blog-vue/admin/src/views/article/Article.vue below 350 lines and keep save/publish/version logic out of templates.
- Add keyboard-accessible preview/version controls, unsaved-change navigation warning, and an explicit last-saved timestamp.
- Add docs/API-CONTRACT.md examples for expectedVersion, 409 conflicts, autosave, preview, publish, and restore.

## Task 4: Add Admin Storage-Provider and Outbox Operations UI

**Files:**

- Add blog-vue/admin/src/views/setting/StorageProviderPanel.vue.
- Add blog-vue/admin/src/views/log/Outbox.vue and register it under the existing log/operations menu group through the dynamic menu contract.
- Modify blog-vue/admin/src/views/setting/Setting.vue, layout/components/NavBar.vue, layout/components/SideBar.vue, assets/js/routeRegistry.js, and assets/js/menuMetadata.js only where route metadata is required.
- Modify blog-vue/shared/api/createApi.js.
- Add blog-vue/admin/src/views/setting/StorageProviderPanel.test.js and OutboxPanel.test.js.

### RED

- Write tests that assert:
  - The panel loads provider statuses and displays only provider, active/configured state, and non-secret validation checks.
  - Validate disables the button while running, shows write/read/delete results, and cannot activate a failed provider.
  - Activating a provider requires a successful validation, updates the active badge, and explains that existing assets retain their original provider.
  - A 429, permission error, or network failure is rendered as a retryable message without showing response internals.
  - Outbox list paginates, displays event type/status/attempts/timestamps/error summary, and manual retry requires confirmation.
- Run focused admin tests. Expected RED result: the current settings screen has only personal information/password tabs and no provider/outbox operations.

### GREEN

- Add shared API methods for storage.providers, storage.validate, storage.activate, outbox.list, outbox.retry, and outbox.metrics.
- Implement StorageProviderPanel as an Options API component with providers, activeProvider, validatingProvider, validationResult, and error. Never bind credential fields or render raw endpoint secrets.
- Show a clear warning before activation: “仅影响之后新上传的对象；已有文章和媒体继续使用创建时记录的存储。” The panel must refresh provider state after activation.
- Implement OutboxPanel with cursor/page controls and bounded displayed payload metadata. Provide retry and refresh actions; no arbitrary event publishing/deleting controls.
- Register menu labels/icons through menuMetadata.js and the existing route registry, preserving API-driven visibility and permission checks.
- Run tests, lint, and build.

### REFACTOR

- Reduce blog-vue/admin/src/layout/components/NavBar.vue below 350 lines by extracting UserMenu.vue, MenuSearch.vue, and NotificationBell.vue only where existing behavior requires them. Keep menu fetching and permission normalization in one shared helper.
- Add an admin UI contract assertion that provider pages contain no strings matching access-key/secret/token field names.

## Task 5: Split and Harden the Public Chatroom and Backend Realtime Protocol

**Files:**

- Add blog-vue/blog/src/components/chat/ChatHeader.vue.
- Add blog-vue/blog/src/components/chat/ChatMessageList.vue.
- Add blog-vue/blog/src/components/chat/ChatComposer.vue.
- Add blog-vue/blog/src/components/chat/VoiceRecorder.vue.
- Add blog-vue/blog/src/components/chat/chatState.js.
- Modify blog-vue/blog/src/components/ChatRoom.vue.
- Add blog-vue/blog/src/components/chat/chatState.test.js, ChatMessageList.test.js, and VoiceRecorder.test.js.
- Modify blog-springboot/src/main/java/com/wzh/blog/service/ChatApplicationService.java, ChatBroadcastService.java, ChatConnectionRegistry.java, service/impl/WebSocketServiceImpl.java, dto/WebsocketMessageDTO.java, and related chat policy/identity classes.
- Add blog-springboot/src/test/java/com/wzh/blog/service/ChatProtocolContractTest.java and ChatReconnectIdempotencyTest.java.

### RED

- Frontend tests must assert:
  - Connecting shows connecting, then online, reconnecting, or offline status.
  - A transient close schedules bounded reconnect; an intentional close schedules none.
  - The same server eventId is appended once; history pages prepend only unseen records.
  - Send failure keeps the draft and exposes a retry action; successful ACK clears the pending message exactly once.
  - VoiceRecorder stops tracks, closes its AudioContext/recorder, clears object URLs, and resets state on cancel, error, and unmount.
  - Message rendering uses the safe HTML directive and does not execute raw event attributes.
- Backend tests must assert:
  - Every outbound event has eventId, serverTime, and a stable type.
  - A duplicate client message ID is persisted/broadcast once and returns the original ACK.
  - History pagination is bounded and does not duplicate records when a page overlaps.
  - Disconnect removes the connection and does not broadcast to a closed session.
- Run public Vitest and backend chat tests. Expected RED result: ChatRoom.vue combines connection, messages, recorder, and menus; the shared client has no ACK/reconnect protocol and backend DTOs do not enforce the envelope.

### GREEN

- Make ChatRoom.vue a coordinator below 250 lines. ChatHeader handles status/count, ChatMessageList handles virtual/anchored list and history, ChatComposer handles text/emoji, and VoiceRecorder owns media resources.
- Define client chat events:

  ~~~text
  CHAT_HISTORY_REQUEST { beforeId, limit }
  CHAT_MESSAGE_SEND { clientMessageId, type, content }
  CHAT_MESSAGE_ACK { clientMessageId, messageId }
  CHAT_MESSAGE { eventId, messageId, ... }
  CHAT_MESSAGE_RECALL { messageId }
  CHAT_ERROR { clientMessageId, code, message }
  ~~~

- Add a client message state pending -> sent | failed; retry reuses clientMessageId so backend idempotency returns the existing ACK instead of creating a duplicate.
- Use the shared reconnect policy with maximum 30-second delay, one active socket, status text, and history reload after reconnect using the latest known message ID. Deduplicate by eventId and message ID.
- Keep chat history in MySQL and local WebSocket registry/broadcast for the single-instance target. Do not route chat through Redis Streams.
- Bound message size/type server-side, apply Phase-1 rate limits, sanitize text/voice URLs, and reject messages from disabled users. ChatConnectionRegistry must remove closed sessions in finally/close callbacks.
- Ensure recorder tracks, media streams, object URLs, and audio elements are released in every path. Do not keep an audio object in a module-global collection.
- Run focused frontend/backend tests, then public lint/build.

### REFACTOR

- Remove index-based Vue keys for messages; use stable messageId/clientMessageId.
- Add a browser test that turns off the network, sends a message, restores it, and verifies one retry/one persisted message.
- Update docs/API-CONTRACT.md with chat envelopes, ACK/idempotency, reconnect, history cursor, and error behavior.

## Task 6: Add Static SEO Output, Feeds, and Responsive Image Variants

**Files:**

- Add blog-vue/blog/scripts/prerender.mjs.
- Add blog-vue/blog/scripts/generate-site-feeds.mjs.
- Add blog-vue/blog/scripts/optimize-images.mjs.
- Add blog-vue/blog/src/utils/seo.js.
- Modify blog-vue/blog/index.html, src/App.vue, src/router/index.js, vite.config.js, and package.json.
- Generate robots.txt, sitemap.xml, and feed.xml under dist during the build; do not add generated feed files to blog-vue/blog/public or source control.
- Add blog-vue/blog/src/utils/seo.test.js and scripts/prerender.test.mjs.
- Modify blog-vue/blog/package-lock.json with the exact Playwright and Sharp dependency entries required by this plan; do not regenerate unrelated dependency versions.

### RED

- Write tests that assert:
  - seo.js produces escaped title/description/canonical, Open Graph, Twitter, and Article JSON-LD values from metadata.
  - A title containing HTML/quotes cannot inject tags into generated HTML.
  - The prerender script creates dist/articles/{id}/index.html for each published article returned by the API, includes an H1 and a bounded sanitized Markdown body, and leaves the SPA shell available for unknown routes.
  - Sitemap contains only public article URLs and uses the configured public origin; robots and feed contain the same origin and no provider endpoint.
  - Image optimization emits WebP/AVIF variants and width-specific files, with no unbounded original upload copied into the public bundle.
- Run:

  ~~~powershell
  npm run test:run -- src/utils/seo.test.js scripts/prerender.test.mjs
  ~~~

- Expected RED result: the current app updates little or no route-specific SEO metadata, has no prerender/feed pipeline, and serves static images without a variant-generation contract.

### GREEN

- Add build scripts:

  ~~~json
  {
    "build:seo": "npm run build && node scripts/prerender.mjs",
    "generate:feeds": "node scripts/generate-site-feeds.mjs",
    "optimize:images": "node scripts/optimize-images.mjs"
  }
  ~~~

- seo.js must use text escaping and return title, description, canonical, Open Graph title/description/url/type/image, Twitter card/title/description/image, and Article JSON-LD with headline, datePublished, dateModified, author, image, and mainEntityOfPage.
- prerender.mjs uses Node's built-in fetch against PRERENDER_API_URL, defaulting only in the build tool to the configured local API URL. It calls the public archive/list endpoint, then metadata/content endpoints, reads dist/index.html, replaces the app mount with a static article shell, and writes dist/articles/{id}/index.html. Markdown-It must use html: false; the generated body is escaped/sanitized before writing. The script must fail if the API returns a private/deleted article or if content is missing, rather than generating an indexable empty page.
- generate-site-feeds.mjs creates dist/sitemap.xml, dist/robots.txt, and dist/feed.xml from public metadata only. The public origin comes from PUBLIC_SITE_ORIGIN and must be an absolute HTTPS URL in production builds. Escape XML text and cap feed content to a configured summary length.
- src/utils/seo.js updates document head for client-side navigation as a fallback. App.vue removes stale route-specific tags when a route changes.
- optimize-images.mjs imports Sharp as a required dependency and fails the build when Sharp cannot load. It creates WebP/AVIF and widths 320, 640, 960, 1440 for approved static assets; preserve SVGs only when they pass the existing safe-asset check. Article upload variants remain a backend/media concern, but the public renderer consumes srcset/sizes.
- Add Vite preview/static fallback rules so /articles/{id}/ serves its generated index.html and unknown paths still serve the SPA shell. Do not add provider URLs to generated files.
- Run focused tests and npm run build:seo against a controlled API fixture.

### REFACTOR

- Add explicit width, height, decoding=async, loading=lazy for below-the-fold images; keep only the hero image eager.
- Add a build assertion that fails when HTML includes third-party icon/CDN script tags not present in the approved allowlist. Prefer the local bundled MDI assets already present.
- Document PRERENDER_API_URL, PUBLIC_SITE_ORIGIN, feed generation, deployment rewrite rules, and cache invalidation in blog-vue/README.md and docs/DEPLOYMENT-CONTRACT.md.

## Task 7: Split Remaining Large Screens and Add Frontend Performance/Accessibility Guards

**Files:**

- Add blog-vue/admin/src/layout/components/UserMenu.vue, MenuSearch.vue, and NotificationBell.vue, extracting the corresponding existing NavBar.vue responsibilities into these three components.
- Add blog-vue/admin/src/views/home/DashboardCards.vue, DashboardCharts.vue, and dashboardState.js.
- Modify blog-vue/admin/src/layout/components/NavBar.vue, layout/index.vue, and views/home/Home.vue.
- Modify blog-vue/blog/src/views/home/Home.vue, src/components/layout/TopNavBar.vue, and src/components/layout/SideNavBar.vue only for measured render/query improvements.
- Add blog-vue/admin/src/views/home/dashboardState.test.js, DashboardCharts.test.js, and blog-vue/blog/src/views/home/Home.test.js.
- Modify blog-vue/admin/scripts/check-bundle-budget.mjs and add a public equivalent under blog-vue/blog/scripts/check-bundle-budget.mjs.

### RED

- Write tests that assert:
  - Admin dashboard renders cards/charts from independent request states; one failed chart does not blank the cards.
  - Route/menu changes do not trigger duplicate menu API calls or duplicate global event listeners.
  - Public home renders a skeleton first and avoids loading chat/recommendations before the main content is available.
  - All interactive controls have accessible names, keyboard activation, visible focus, and loading/disabled states.
  - Bundle checks fail when the largest JS chunk exceeds 500 KB or total initial JS exceeds 512 KB for either app; retain the existing admin thresholds unless a measured, reviewed change is needed.
- Run focused tests and current budget scripts. Expected RED result: the named large files contain multiple responsibilities and only the admin app has a formal budget check.

### GREEN

- Move only stateful responsibilities into the extracted components; keep shared store mutations and API calls in dashboardState.js/the page coordinator.
- Lazy-load charts and non-critical admin panels with route/component dynamic imports. Do not lazy-load the first article metadata/content path.
- Prevent duplicate requests with a page-scoped request key and cancel/ignore stale responses. Use the backend batch endpoints from Phase 2 rather than one request per card.
- Add aria-label, aria-live for status/errors, keyboard handlers for custom buttons, alt text, form labels, and focus return for dialogs. Do not rely on color alone for provider status or chat connection state.
- Add public and admin bundle-budget scripts and CI commands. Use actual built asset sizes, excluding source maps, and fail nonzero with the largest offenders printed.
- Run focused Vitest tests, lint, build, and both budget scripts.

### REFACTOR

- Keep NavBar.vue below 350 lines and Home.vue below 350 lines; keep extracted components below 300 lines unless a test demonstrates one cohesive responsibility.
- Add a small browser performance smoke that records LCP/INP proxies and fails only on clear regressions: LCP over 2.5s on the local reference fixture or a long task over 200ms during initial article render.
- Record bundle and performance baselines in CI artifacts instead of silently changing thresholds.

## Task 8: Add Browser Smoke, CI Matrix, and Final Delivery Verification

**Files:**

- Add blog-vue/blog/playwright.config.js.
- Add blog-vue/blog/tests/e2e/public-smoke.spec.js.
- Add blog-vue/blog/tests/e2e/admin-smoke.spec.js; the Playwright configuration runs it against the admin preview base URL.
- Modify blog-vue/blog/package.json, blog-vue/admin/package.json, and the relevant npm lockfile only for required test scripts/dependencies.
- Modify .github/workflows/verify.yml, compose.yaml, scripts/verify-architecture.sh, scripts/verify-proxy-contract.sh, scripts/verify-compose-contract.sh, README.md, docs/OPERATIONS-RUNBOOK.md, and docs/ARCHITECTURE-ROADMAP.md.

### RED

- Add browser tests for the minimum approved flows:
  - Public home -> article list -> article metadata/content -> search -> comment/message form validation.
  - Article content endpoint failure -> visible retry -> successful content load.
  - Offline/online chat reconnect with one message ACK.
  - Admin login -> article edit -> autosave -> preview -> publish -> version list/restore.
  - Admin provider status -> validate -> activate, with no credential input/output.
  - Admin Outbox view -> manual retry.
  - Direct navigation to a generated /articles/{id}/ file includes title, canonical, H1, and article JSON-LD before JavaScript runs.
- Run the browser suite against a controlled local stack. Expected RED result: the current build has no end-to-end harness and several flows are coupled to the old combined endpoints.

### GREEN

- Add Playwright as a dev dependency only where the repository's Node package policy places E2E tooling. Use a fixture API or seeded local MySQL/object-store stack; tests must not call real COS/OSS/TOS credentials.
- Configure webServer entries for API, public preview, and admin preview with explicit health checks. Use environment variables for base URLs and test credentials; do not commit credentials.
- Add npm scripts:

  ~~~json
  {
    "test:e2e": "playwright test",
    "test:e2e:report": "playwright show-report"
  }
  ~~~

- Update CI with these ordered gates:

  ~~~text
  backend clean test/package
  MySQL Flyway integration
  Redis disabled core flow
  Redis enabled/failed-stream flow
  public lint/test/build/build:seo/budget
  admin lint/test/build/budget
  architecture/proxy/Compose contracts
  browser smoke
  performance/security artifact checks
  ~~~

- Ensure the workflow starts only the services needed by each job. The default Compose job must not start RabbitMQ or Elasticsearch; the Redis job uses compose.yaml -f compose.redis.yaml.
- Make missing Docker explicit: integration/browser jobs fail or are marked skipped with a visible reason, never report a partial unit-only run as a full pass.
- Run all fresh commands from the repository root and preserve reports in ignored CI artifact paths.

### REFACTOR

- Review every changed frontend/backend file with git diff --check and search for hardcoded endpoints, provider URLs, credentials, unsafe HTML sinks, and stale legacy article-content fields.
- Validate deployment rewrites for generated article files, API proxy behavior, WebSocket upgrade headers, cache headers, and robots.txt/feed content.
- Update the runbook with startup order, Redis-on/off commands, object-provider activation, search rebuild, Outbox retry, browser smoke prerequisites, and rollback boundaries.

## Phase-3 Completion Checklist

- [ ] Public article metadata, Markdown content, comments, and recommendations load independently with visible retry/error states.
- [ ] Admin Markdown editing supports autosave, preview, publish, conflict handling, version history, and restore against immutable content assets.
- [ ] Provider and Outbox operations are usable from the admin UI without exposing credentials or arbitrary object operations.
- [ ] Chat reconnects with bounded backoff, deduplicates events, retries sends idempotently, pages history, and releases recorder/audio resources.
- [ ] Public article pages have prerendered metadata/content, canonical/OG/Twitter/JSON-LD, sitemap, robots, RSS/Atom, and responsive image variants.
- [ ] Large screens are split by responsibility while preserving Options API and existing routes/menu contracts.
- [ ] Bundle, accessibility, browser smoke, build, Redis matrix, security, and performance checks are visible CI gates with honest skipped-test reporting.
