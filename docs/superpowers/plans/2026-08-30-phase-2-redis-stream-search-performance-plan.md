# Phase 2 Optional Redis, Durable Events, Search, and Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the second-priority reliability and performance work after Phase 1: make Redis explicitly optional, replace RabbitMQ-dependent delivery with a MySQL Outbox plus an optional Redis Stream transport, remove Elasticsearch from the default runtime, add a rebuildable local article search index, and fix the highest-cost query/cache/media paths.

**Architecture:** MySQL remains the only durable fact source. Every asynchronous side effect starts as a MySQL Outbox row. With Redis enabled, a bridge publishes envelopes to Redis Streams and consumer groups process them; with Redis disabled or unavailable, a database worker invokes the same idempotent handlers. Caches, rate limits, locks, and deduplication are adapters behind ports with bounded in-process fallbacks. Public article search uses a local Lucene index under a persistent data directory and can be rebuilt from object storage.

**Tech Stack:** Java 21, Spring Boot 4.1, MyBatis/MyBatis-Plus, Flyway, MySQL 8, Redis 7/Redis Streams, Apache Lucene 9.12.1, JUnit 5, Testcontainers when Docker is available, Vue/Vite build budgets.

**Spec:** [Ticastr single-instance architecture design](../specs/2026-08-30-ticastr-single-instance-design.md)

## Global Constraints

- Phase 1 must be complete before this plan starts. Do not reintroduce 'tb_article.article_content', Redis-only counters, provider URL construction in Vue, RabbitMQ, or Elasticsearch.
- Work around existing dirty files. Never reset the repository or use a broad cleanup command. Stage only files named by the task when creating commits.
- Use RED -> GREEN -> REFACTOR for every behavior. A focused test must fail for the intended reason before implementation.
- Outbox delivery is at-least-once. Every handler must be idempotent by eventId; an ACK is valid only after the handler has committed its effect.
- Redis is a performance/reliability accelerator, never a prerequisite for article reads, content writes, comments, messages, likes, provider configuration, or chat history.
- Redis Streams are for email, content-index updates, media cleanup, notifications, and similar asynchronous work. Never use a Stream as the only source of chat history or article facts.
- Do not perform blind connection-pool, index, or cache changes. Each query/performance change must have an explainable access path and a before/after measurement.
- Run fresh backend, frontend, architecture, proxy, and Compose checks before declaring this phase complete. Skipped Docker-dependent tests must be reported separately.

## Dependency Order

1. Remove default runtime coupling and add the explicit Redis feature switch.
2. Generalize the Outbox and implement the database worker.
3. Add the optional Redis Stream bridge and consumer recovery behavior.
4. Put cache, rate-limit, idempotency, and locking behind optional ports.
5. Replace MySQL body search with the local rebuildable index.
6. Tune article/home/admin/online-user query paths and caches.
7. Make media cleanup and reconciliation durable and observable.
8. Run controlled Redis matrix, search rebuild, integration, and performance gates.

---

## Task 1: Remove RabbitMQ/Elasticsearch from the Default Runtime and Add the Redis Switch

**Files:**

- Modify 'blog-springboot/pom.xml'.
- Modify 'blog-springboot/src/main/resources/application.yml' and 'application-local.example.yml'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/config/RedisFeatureProperties.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/config/RedisOptionalConfiguration.java'.
- Modify or delete 'blog-springboot/src/main/java/com/wzh/blog/config/RabbitMQConfig.java', 'config/RedisConfig.java', and 'config/RedisMessagingConfig.java'.
- Modify or delete 'blog-springboot/src/main/java/com/wzh/blog/consumer/EmailConsumer.java', 'consumer/MaxWellConsumer.java', 'strategy/impl/EsSearchStrategyImpl.java', and 'strategy/context/SearchStrategyContext.java' after callers are migrated.
- Modify 'compose.yaml', 'deploy/backend/compose.yaml', '.env.example', 'deploy/backend/.env.example', and '.github/workflows/verify.yml'.
- Add 'compose.redis.yaml' as the explicit Redis-enabled Compose override.
- Add 'blog-springboot/src/test/java/com/wzh/blog/config/RuntimeDependencyContractTest.java' and 'RedisAvailabilityModeTest.java'.

### RED

- Write 'RuntimeDependencyContractTest' to inspect the application dependency graph/configuration and assert that default startup does not require 'spring-boot-starter-amqp', Elasticsearch client beans, a RabbitMQ host, or an Elasticsearch host. Write 'RedisAvailabilityModeTest' to start the application context with 'app.redis.enabled=false' and assert no Redis connection factory, Redis session repository, Redis listener container, or Redis Stream consumer is created.
- Run:

  ~~~powershell
  mvn -B -Dtest=RuntimeDependencyContractTest,RedisAvailabilityModeTest test
  ~~~

- Expected RED result: the current POM/configuration contains AMQP/Elasticsearch integration and creates Redis-related beans without one explicit application-level switch.

### GREEN

- Add 'app.redis.enabled' with default 'false', 'app.redis.key-prefix' default 'ticastr', and 'app.redis.streams.*' settings for stream prefix, consumer group, batch size, claim timeout, and max delivery attempts.
- Remove RabbitMQ and Elasticsearch dependencies/configuration from the default POM and runtime. Keep mail sending as an Outbox handler; do not keep a dead RabbitMQ adapter just in case.
- Retain the Redis client dependency as optional infrastructure. 'RedisOptionalConfiguration' must be conditional on 'app.redis.enabled=true', create the connection factory/template only in that mode, and set serializers to String keys plus JSON values. Redis session configuration must also be conditional; cookie/session behavior must still work in the disabled profile through the normal servlet session setup.
- Make active-provider, local storage, and local search startup independent of Redis.
- Change 'compose.yaml' so the default graph contains MySQL, API, public, and admin only. Add 'compose.redis.yaml' as an explicit override that adds Redis and sets 'APP_REDIS_ENABLED=true'; API health checks must not wait on Redis in the base file.
- Update CI to run a Redis-disabled backend test job unconditionally and a Redis-enabled job only with the Redis service/container.
- Run the focused tests and 'mvn -B -DskipTests package' until green.

### REFACTOR

- Make the runtime contract script reject 'rabbitmq', 'elasticsearch', 'amqp', or 'spring-boot-starter-amqp' in default Compose/POM/configuration, while allowing documentation to mention migration history.
- Delete obsolete 'MQPrefixConst', Rabbit consumers/config, and ES strategy only after 'rg -n "Rabbit|AMQP|Elasticsearch|ElasticSearch|MaxWell|spring\\.elasticsearch" blog-springboot compose.yaml deploy' returns no runtime references.
- Update 'docs/DEPENDENCY-MATRIX.md', 'docs/CONFIGURATION.md', 'docs/DEPLOYMENT-CONTRACT.md', and 'docs/OPERATIONS-RUNBOOK.md' with the two Compose modes and the statement that Redis-disabled behavior is a supported operating mode.

## Task 2: Generalize the MySQL Outbox and Implement the Direct Database Worker

**Files:**

- Add 'blog-springboot/src/main/resources/db/migration/V13__outbox_lifecycle.sql'.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/entity/OutboxEvent.java', 'dao/OutboxEventDao.java', 'resources/mapper/OutboxEventDao.xml', 'service/OutboxEventService.java', 'service/OutboxDispatcher.java', and 'service/DurableEventPublisher.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/OutboxEventHandler.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/OutboxEventHandlerRegistry.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/DatabaseOutboxWorker.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/OutboxMetrics.java'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/jobs/DatabaseOutboxWorkerTest.java', 'OutboxEventHandlerRegistryTest.java', and 'OutboxIdempotencyTest.java'.

### RED

- Write tests for the exact lifecycle:
  - A row with 'PENDING' and a due 'next_attempt_at' is claimed once with 'FOR UPDATE SKIP LOCKED' and becomes 'PROCESSING'.
  - A successful handler invocation moves the row to 'PUBLISHED' and records 'published_at'.
  - A retryable exception increments 'attempts', stores a bounded error message, and schedules exponential backoff in 'PENDING'.
  - A non-retryable exception or attempt count beyond the configured maximum moves the row to 'DEAD'.
  - A stale 'PROCESSING' row is claimable after the claim timeout.
  - A duplicate eventId does not apply a handler effect twice.
  - Unknown eventType is observable as a failed/dead event rather than silently dropped.
- Run the focused job tests. Expected RED result: current dispatch is email-specific, has no handler registry, and does not support 'ENQUEUED', generic event versions, or idempotency.

### GREEN

- Add 'V13__outbox_lifecycle.sql' to add 'event_version' default '1', 'trace_id', 'enqueued_at', 'processing_started_at', and 'processed_at', and to add the 'ENQUEUED' status to the documented lifecycle. Drop 'idx_outbox_dispatch' and create 'idx_outbox_dispatch_v2' on '(status, next_attempt_at, claimed_at, created_at)'.
- Define the envelope as 'DurableEventEnvelope(eventId, eventType, version, aggregateId, occurredAt, traceId, payload)'. Serialize it as JSON in 'tb_outbox_event.payload'; never serialize Java class names.
- Define 'OutboxEventHandler' with 'eventType()', 'handle(DurableEventEnvelope envelope)', and 'isRetryable(Exception error)'. Register handlers by exact event type and reject duplicate registrations at startup.
- Implement 'DatabaseOutboxWorker' as a scheduled, bounded batch worker. Each claim transaction selects at most 'outbox.batch-size' rows, marks them 'PROCESSING', and commits before invoking handlers. Handler effects and final event status are committed separately but idempotently.
- Implement backoff 'min(maxDelay, baseDelay * 2^attempts)' with jitter disabled in unit tests and bounded last-error length of 1000 characters. Add counters for claimed, published, retried, dead, unknown, and handler latency.
- Keep 'OutboxEventService.enqueue(eventType, version, aggregateId, traceId, payload)' as the only creation API. Business transactions insert the row in the same transaction as their fact change.
- Run focused tests and a MySQL mapper integration test until green.

### REFACTOR

- Remove email-specific parsing from 'OutboxDispatcher'; the dispatcher must not cast every payload to 'EmailDTO'.
- Keep 'DurableEventPublisher' as a compatibility facade that creates Outbox envelopes; it must not send directly to a broker.
- Add 'GET /api/admin/outbox', 'POST /api/admin/outbox/{eventId}/retry', and 'GET /api/admin/outbox/metrics' only behind an admin permission. Return payload metadata and error summaries, not secrets or full email bodies.

## Task 3: Add the Optional Redis Stream Bridge and Consumer Recovery

**Files:**

- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisStreamEventTransport.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisStreamConsumer.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisStreamBridgeJob.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisStreamAdminService.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisStreamProperties.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/DurableEventTransport.java' and 'NoopDurableEventTransport.java'.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/service/OutboxDispatcher.java', 'config/RedisOptionalConfiguration.java', and 'config/DeploymentConfigurationValidator.java'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/infrastructure/redis/RedisStreamEventTransportTest.java', 'RedisStreamConsumerTest.java', and 'RedisStreamIntegrationTest.java'.

### RED

- Write unit tests using a fake Redis command client:
  - The bridge reads only 'PENDING' Outbox rows, publishes one envelope with fields 'eventId', 'eventType', 'version', 'aggregateId', 'occurredAt', 'traceId', and 'payload', then marks the row 'ENQUEUED'.
  - A failed 'XADD' leaves the row 'PENDING' for the database worker.
  - The consumer creates the stream and consumer group idempotently, calls the registered handler, and ACKs only after success.
  - A handler retryable error leaves the message pending; a non-retryable/max-delivery message moves to a dead-letter stream and marks the Outbox row 'DEAD'.
  - Pending messages older than 'claim-timeout' are claimed and processed by the recovery consumer.
  - Redis unavailable at startup does not fail the application context when the feature is enabled; the database worker remains active.
- Run the focused tests. Expected RED result: no provider-neutral transport, bridge, consumer group, or pending recovery exists.

### GREEN

- Use stream names '{prefix}:events:{eventType}' and dead-letter names '{prefix}:dead:{eventType}'. Use one configured group name and a stable consumer name derived from the instance ID.
- 'RedisStreamBridgeJob' selects due 'PENDING' rows in small batches, publishes the envelope with 'XADD', and transitions each row to 'ENQUEUED' only when 'XADD' succeeds. If Redis is down, it records a metric and exits without changing the row.
- 'RedisStreamConsumer' uses 'XREADGROUP', handles 'BUSYGROUP' as an idempotent create race, and performs 'XAUTOCLAIM' for pending recovery. ACK after the handler returns. Use bounded polling and no busy loop when the stream is empty.
- Maintain the Outbox lifecycle in MySQL as the authoritative status. A consumer may update 'ENQUEUED -> PROCESSING -> PUBLISHED/DEAD'; a row left 'ENQUEUED' after Redis loss becomes eligible for the database worker after an enqueue lease timeout.
- Register handlers for 'EMAIL_SEND', 'ARTICLE_CONTENT_INDEX', 'MEDIA_DELETE', and 'NOTIFICATION_PUBLISH'. Keep 'CHAT_MESSAGE' out of this transport.
- Add admin read-only stream/Outbox metrics and a manual retry operation that returns the row to 'PENDING'; never expose arbitrary XADD/XDEL controls.
- Run unit tests. Run 'RedisStreamIntegrationTest' with a Redis 7 container when Docker is available; otherwise mark it skipped with the reason.

### REFACTOR

- Make 'NoopDurableEventTransport' the injected implementation when Redis is disabled, but keep the database worker scheduled in both modes.
- Add trace IDs and event IDs to logs/metrics. Do not log payload content, access keys, email tokens, or client message bodies.
- Update 'docs/MESSAGE-RELIABILITY.md' with the state machine, bridge failure behavior, 'XREADGROUP'/'XAUTOCLAIM' recovery, ACK timing, manual retry, and dead-letter procedure.

## Task 4: Put Cache, Rate Limits, Idempotency, and Short Locks Behind Optional Ports

**Files:**

- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/cache/CacheStore.java' and 'InProcessCacheStore.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisCacheStore.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisRateLimitStore.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisIdempotencyStore.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/redis/RedisLockStore.java'.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/security/BoundedInMemoryRateLimitStore.java', 'security/RateLimitPolicy.java', 'service/RateLimitStore.java', 'service/EventDeduplicationStore.java', 'service/LockStore.java', 'service/AuthorizationCacheService.java', and cache callers.
- Add 'blog-springboot/src/test/java/com/wzh/blog/infrastructure/cache/CacheStoreTest.java', 'RedisOptionalFallbackTest.java', 'RedisAtomicRateLimitTest.java', and 'RedisIdempotencyTest.java'.

### RED

- Write contract tests that run each port against an in-process implementation and a fake Redis implementation:
  - Cache 'get', 'put', TTL expiry, and delete are best-effort; an exception becomes a cache miss.
  - Rate limiting is atomic for concurrent increments and returns the same policy result in Redis and fallback modes.
  - Idempotency 'putIfAbsent(key, ttl)' returns false for a duplicate and true after TTL expiry.
  - Lock acquisition returns a token; release only succeeds with the same token; an expired lock cannot be released by a previous owner.
  - Authorization invalidation still works with Redis disabled and never makes a core authorization request fail because Redis is unavailable.
- Run the focused tests. Expected RED result: current services call Redis directly and have no equivalent semantics in the disabled mode.

### GREEN

- Define small ports with explicit failure semantics: cache failures are misses, rate-limit failures fall back to bounded memory, idempotency failures return 'UNAVAILABLE' only for operations that explicitly require deduplication, and lock failures fail the guarded non-core job without corrupting facts.
- Implement Redis values with versioned keys:

  ~~~text
  {prefix}:article:metadata:{articleId}:{updatedAt}
  {prefix}:article:content:{assetId}:{version}
  {prefix}:home:summary
  {prefix}:search:result:{queryHash}
  {prefix}:rate:{policy}:{identityHash}
  {prefix}:idempotency:{scope}:{keyHash}
  {prefix}:lock:{name}
  ~~~

- Use Lua or a single Redis atomic operation for rate-limit check/increment/TTL. Hash or HMAC client identity before putting it into a Redis key.
- Use 'SET key value NX PX' for locks and a compare-and-delete Lua script for release. Never use an unbounded lock renewal loop.
- Reuse the Phase-1 bounded in-process rate limiter when Redis is disabled/unavailable. Cap cache entries and evict by TTL plus oldest access; do not use an unbounded 'ConcurrentHashMap'.
- Replace direct Redis calls in 'EngagementService', 'AuthorizationCacheService', 'DistributedLockService', online-session code, and controller-level deduplication with the ports.
- Add cache-aside behavior to article metadata/content reads, but treat cache serialization or eviction as non-fatal. MySQL/object storage remain the miss path.
- Run all focused tests and a Redis integration test with concurrent threads.

### REFACTOR

- Centralize key construction in 'CacheKeyFactory'; no service concatenates raw user input into Redis keys.
- Add metrics for cache hits/misses/errors, fallback activations, rate-limit denials, lock contention, and idempotency duplicates.
- Document which operations remain available with Redis off in 'docs/REDIS-CONTRACT.md'; include a failure-injection test that stops Redis and then successfully reads/publishes an article through the fallback path.

## Task 5: Replace MySQL Body Search with a Rebuildable Embedded Index

**Files:**

- Modify 'blog-springboot/pom.xml' to pin Lucene '9.12.1' and add 'lucene-core', 'lucene-queryparser', and 'lucene-analyzers-common' at the same version.
- Add 'blog-springboot/src/main/java/com/wzh/blog/search/ArticleSearchIndex.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/search/ArticleSearchDocument.java' and 'ArticleSearchResult.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/infrastructure/search/LuceneArticleSearchIndex.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/search/ArticleSearchApplicationService.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/ArticleSearchIndexHandler.java'.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/strategy/SearchStrategy.java', 'strategy/context/SearchStrategyContext.java', 'strategy/impl/MySqlSearchStrategyImpl.java', 'service/impl/ArticleServiceImpl.java', and 'resources/mapper/ArticleDao.xml'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/search/LuceneArticleSearchIndexTest.java', 'SearchRebuildServiceTest.java', and 'SearchQueryContractTest.java'.

### RED

- Write tests that:
  - Index a published article with title, category, tags, and Markdown body; search finds it by title, tag, and body terms.
  - Search excludes private/deleted articles and returns stable article IDs, title, highlight/snippet, and score.
  - Updating an article replaces the old indexed document; deleting/unpublishing removes it.
  - A closed/corrupted index is rebuilt from an 'ArticleContentReader' plus MySQL metadata without requiring 'article_content'.
  - Search query size is bounded, empty input is rejected, special query syntax is escaped, and result size is capped.
  - 'ArticleDao.xml' contains no 'LIKE' predicate against article content.
- Run the focused tests. Expected RED result: current search uses MySQL 'LIKE' over 'article_content' and the ES strategy is either unavailable or a default dependency.

### GREEN

- Use a configurable persistent directory 'app.search.index-path' defaulting to 'data/search-index'. Create it only after validating it is inside the configured application data root; do not allow a request to choose the path.
- Store Lucene fields: 'articleId' as a keyword, 'title' as analyzed/stored, 'categoryId' as keyword, 'tagNames' as analyzed/stored, 'body' as analyzed/not stored, and 'snippetSource' as a stored field truncated to 2000 UTF-16 code units. The object store remains the canonical Markdown source.
- Use 'StandardAnalyzer' for title/tags and a character n-gram field for mixed Chinese/Latin body lookup. Escape query syntax, cap the query at 100 UTF-8 bytes, and return at most 20 results.
- Make index updates atomic with a writer commit. A handler reads metadata from MySQL and Markdown from 'ArticleContentService', skips unpublished/deleted articles, and deletes the old document before adding the new one.
- Implement 'rebuildAll()' to enumerate published article IDs from a paged DAO, read each current content asset through the provider-neutral content service, write to a temporary index directory, fsync/close it, then atomically replace the active index directory. A failed rebuild leaves the existing index untouched.
- Publish 'ARTICLE_CONTENT_INDEX' Outbox events from the article content transaction. Index failure causes retry/dead-letter handling but does not roll back article publication.
- Change 'SearchStrategyContext' to use the embedded index only. Remove 'MySqlSearchStrategyImpl' once no caller remains; do not keep a fallback that performs a body 'LIKE' query.
- Run focused tests and a rebuild test with a temporary object-store fake.

### REFACTOR

- Add a startup health indicator reporting index path, document count, last rebuild time, and stale/degraded state without exposing article content.
- Cache only normalized query results for a short TTL through 'CacheStore'; invalidation is best-effort and a stale result must never expose a private article.
- Document rebuild, index corruption, disk-full behavior, and the distinction between search index and content source in 'docs/OPERATIONS-RUNBOOK.md'.

## Task 6: Fix Cursor Pagination, Article/Home Caches, and Admin Query Fan-Out

**Files:**

- Add 'blog-springboot/src/main/java/com/wzh/blog/web/CursorPageQuery.java' and 'CursorCodec.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/web/CursorPageResult.java'.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/controller/ArticleController.java', 'vo/ArticleQueryVO.java', 'service/ArticleService.java', 'service/impl/ArticleServiceImpl.java', 'dao/ArticleDao.java', and 'resources/mapper/ArticleDao.xml'.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/service/impl/BlogInfoServiceImpl.java', 'controller/PageController.java', and admin dashboard DAO/mapper files.
- Modify 'blog-springboot/src/main/java/com/wzh/blog/service/OnlineSessionService.java' and the DAO used for online users.
- Add 'blog-springboot/src/main/resources/db/migration/V14__query_path_indexes.sql'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/web/CursorPaginationTest.java', 'ArticleQueryPlanIntegrationTest.java', 'AdminDashboardBatchQueryTest.java', and 'OnlineSessionPageTest.java'.

### RED

- Write tests that:
  - A public article list returns 'items', 'nextCursor', and 'hasNext'; the second page starts strictly after '(create_time,id)' from the cursor and has no duplicates.
  - A tampered/expired cursor returns a stable 400.
  - An invalid article ID triggers no recommendation/latest query; a valid detail does not load Markdown while building metadata.
  - Counts for a page of N articles are fetched with one batched DAO call, not N Redis/DAO calls.
  - The dashboard loads each chart/card through bounded page queries and a short cache instead of a full Redis Hash/ZSet scan.
  - Online-user pagination does not enumerate every account and then perform one Session lookup per account.
- Run focused tests. Expected RED result: public pagination is offset-based/legacy, dashboard paths fan out, and the service starts recommendation work before all invalid-ID exits.

### GREEN

- Use a signed/base64 cursor containing 'createTime', 'id', filter fingerprint, and expiration. Query with:

  ~~~sql
  WHERE is_delete = 0
    AND status = 1
    AND (create_time < #{cursorTime}
      OR (create_time = #{cursorTime} AND id < #{cursorId}))
  ORDER BY create_time DESC, id DESC
  LIMIT #{pageSizePlusOne}
  ~~~

- Keep admin offset pagination only where the UI needs random page numbers; use cursor pagination for public article/archive/search feeds and bound every page size at 50.
- Validate the article ID and visibility in one metadata query before loading previous/next/recommendation/latest data. Fetch those related lists only after the article exists and only from metadata tables.
- Add cache-aside metadata keys from the Phase-2 'CacheKeyFactory', invalidate article metadata/home summary after MySQL commit, and keep cache errors non-fatal. Do not cache private/draft content in a public key.
- Replace dashboard full-structure reads with page-scoped SQL aggregates and 'HMGET'/'ZMSCORE' for the visible IDs when Redis is active. The disabled path reads the same values from MySQL.
- Add 'V14__query_path_indexes.sql' only for measured predicates: '(is_delete,status,create_time,id)' for public feeds, '(article_id,create_time)' for engagement/event reads, and indexes required by current-page admin filters. Verify each with 'EXPLAIN' in the integration test.
- Change online-session lookup to maintain a bounded session index keyed by user ID and retrieve only the requested page; stale IDs are removed during reads instead of scanning every user.
- Run focused tests plus MySQL explain tests with seeded rows.

### REFACTOR

- Return a consistent page shape from public endpoints and update 'docs/API-CONTRACT.md' with cursor examples and invalid-cursor errors.
- Add timing metrics around metadata, content, recommendations, dashboard modules, and session page queries. Do not claim p95 improvements until a repeatable benchmark is run.
- Keep SQL projections explicit; never restore 'SELECT *' to solve DTO mapping issues.

## Task 7: Make Media Cleanup and Asset Reconciliation Durable

**Files:**

- Modify 'blog-springboot/src/main/java/com/wzh/blog/media/AssetLifecycleService.java', 'media/MediaAssetStore.java', 'infrastructure/media/DatabaseMediaAssetLedger.java', and 'infrastructure/media/DatabaseMediaReferenceChecker.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/media/AssetReconciliationService.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/jobs/MediaCleanupHandler.java' and 'MediaReconciliationJob.java'.
- Add 'blog-springboot/src/main/java/com/wzh/blog/dao/MediaAssetDao.java' and mapper methods for page-scoped status/reference reads.
- Add 'blog-springboot/src/main/resources/db/migration/V15__media_asset_source_type.sql'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/media/AssetReconciliationServiceTest.java', 'MediaCleanupHandlerTest.java', and 'MediaProviderFailureIntegrationTest.java'.
- Modify 'blog-springboot/src/main/resources/db/migration/V9__media_asset_ledger.sql' only through a new migration if a column/index change is needed; do not edit an already-applied migration.

### RED

- Write tests that:
  - A retired asset with no references creates one 'MEDIA_DELETE' Outbox event and a successful handler marks it deleted after provider deletion.
  - A provider delete is idempotent for a missing object.
  - A failed provider delete leaves 'DELETE_FAILED', records a bounded error, and is retried with backoff.
  - A reconciliation run finds an object ledger row missing from MySQL references without scanning all unrelated tables in one unbounded transaction.
  - An asset referenced by an article, photo, cover, or talk is never deleted.
  - Provider switching does not make an old asset eligible merely because it is not in the active provider.
- Run focused tests. Expected RED result: current reference checks and cleanup are synchronous/scan-heavy and do not have a generic Outbox handler.

### GREEN

- Treat 'tb_media_asset' and 'tb_content_asset' as ledger sources with provider/object key/checksum/size/status. Add 'source_type varchar(16) CHARACTER SET ascii NOT NULL DEFAULT ''MEDIA''' to 'tb_media_asset' through 'V15__media_asset_source_type.sql', and set content assets to source type 'CONTENT' in their own table; never infer source type from object-key prefixes.
- Replace inline provider deletion with an idempotent 'MEDIA_DELETE' Outbox event. The handler loads the ledger row, checks current references through indexed queries, calls the recorded provider, and marks 'DELETED' or 'DELETE_FAILED'.
- Implement 'MediaReconciliationJob' in bounded pages: identify 'PENDING', 'RETIRED', 'DELETE_FAILED', and stale 'PENDING' assets; enqueue or retry events without holding a transaction while calling a provider.
- Make reference checks explicit for articles/content assets, photos, website config, albums, talks, avatars, and any existing media source. Each checker returns a boolean/count from an indexed query rather than loading all rows.
- Add metrics for orphan candidates, protected references, delete success/failure, oldest pending age, and provider latency.
- Run focused tests and controlled provider failure tests.

### REFACTOR

- Add an admin reconciliation report with counts and oldest failure timestamps, plus a manual retry endpoint for one asset/event. Do not expose arbitrary object keys for deletion.
- Update 'docs/MEDIA-LIFECYCLE.md' with asset state transitions, provider identity retention, Outbox cleanup, reconciliation cadence, and manual recovery.

## Task 8: Run the Redis Matrix, Search Rebuild, Performance, and Phase-2 Delivery Gate

**Files:**

- Modify '.github/workflows/verify.yml', 'scripts/verify-architecture.sh', 'scripts/verify-compose-contract.sh', 'scripts/verify-proxy-contract.sh', 'README.md', 'docs/DEPENDENCY-MATRIX.md', 'docs/MESSAGE-RELIABILITY.md', 'docs/REDIS-CONTRACT.md', 'docs/OPERATIONS-RUNBOOK.md', and 'docs/ARCHITECTURE-ROADMAP.md'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/phase2/RedisModeCoreFlowIntegrationTest.java'.
- Add 'blog-springboot/src/test/java/com/wzh/blog/phase2/SearchRebuildIntegrationTest.java'.
- Add 'scripts/measure-api-baseline.mjs' or an equivalent repository-supported benchmark script.

### RED

- Add matrix tests that exercise the same core flow with 'app.redis.enabled=false', Redis available, and Redis stopped after startup. The flow must create/read/update an article, read Markdown, comment, message, like, run an Outbox event, and search.
- Add a rebuild integration test that deletes the local index directory, runs 'rebuildAll()', and verifies body search results return without MySQL body text.
- Add a benchmark assertion harness for seeded data that records p50/p95 for article list, metadata hit/miss, content read, search, and dashboard page. Run before query/cache changes to create a baseline artifact.
- Expected RED result: current runtime has no valid three-mode matrix, no index rebuild path, and no repeatable metrics artifact.

### GREEN

- Run the Redis-disabled profile with no Redis service and assert core success. Run the enabled profile with Redis 7 and assert cache/stream/rate-limit paths. Stop Redis during the run and assert Outbox rows remain recoverable and core reads/writes use fallback behavior.
- Run the search rebuild against a temporary object store and fresh local index directory; verify the index can be deleted and reconstructed.
- Seed at least 10,000 article metadata rows and 100,000 engagement/Outbox rows in the benchmark database. Record exact machine/runtime/database configuration alongside measurements. Use the approved targets as acceptance goals: article list p95 < 200 ms, Redis-hit detail p95 < 150 ms, cache-miss/object-read detail p95 < 500 ms, search p95 < 300 ms.
- Add CI jobs for backend clean test/package, MySQL integration, Redis off/on/failure matrix, frontend checks, bundle budgets, architecture/proxy/Compose checks, and the benchmark smoke threshold. Ensure a missing Docker service is a visible skipped/failed condition rather than a green integration result.
- Run all fresh verification commands from the repository root and save reports under ignored build/artifact directories.

### REFACTOR

- Review 'git diff --check', dependency tree, Compose graph, and runtime logs for credentials/payload leakage.
- Verify:

  ~~~powershell
  rg -n "article_content|LIKE.*content|Rabbit|AMQP|Elasticsearch|spring\\.elasticsearch|XREADGROUP|XADD|data/search-index" blog-springboot compose.yaml deploy docs
  ~~~

  Runtime matches must show only the intended Outbox/Redis/search implementation and documentation; MySQL body search and Rabbit/ES runtime references must be gone.
- Update the phase report with test counts, Redis modes, Docker availability, benchmark values, and any explicitly skipped real-provider tests.

## Phase-2 Completion Checklist

- [ ] Base Compose and default Spring Boot startup do not require RabbitMQ, Elasticsearch, or Redis.
- [ ] Redis enabled mode supplies cache, atomic rate limits, idempotency, locks, and Redis Streams; disabled/unavailable mode uses safe fallbacks.
- [ ] MySQL Outbox is generic, transactional, observable, idempotent, retryable, recoverable, and manually retryable.
- [ ] Redis Stream bridge and consumer group ACK only after successful handler effects; pending/dead-letter recovery is tested.
- [ ] Search has no MySQL body 'LIKE', uses a local rebuildable index, and publishes updates through Outbox.
- [ ] Public feeds use cursor pagination; article/home/admin/session paths avoid avoidable fan-out and full-structure Redis reads.
- [ ] Media cleanup is asynchronous, provider-aware, reference-safe, and reconcilable.
- [ ] Redis matrix, fresh search rebuild, integration tests, and measured performance thresholds are in CI with honest skipped-test reporting.
