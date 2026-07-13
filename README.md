# ticastr

Personal blog system with a Spring Boot REST API, a public blog, and an administrator console.

## Technology baseline

- API: Java 21, Spring Boot 4.1, Spring Security 7, MyBatis-Plus, Springdoc OpenAPI, WebSocket.
- Services: MySQL 8, Redis, RabbitMQ, Elasticsearch, mail, and object storage.
- Web apps: Node.js 24, Vite 8, Vue 3.5, Vue Router 5, Vuex 4, Axios 1.
- UI migration: the public site retains a Vuetify 2 compatibility layer and the console retains a small number of legacy Vue plug-ins through `@vue/compat`. This keeps the existing views working while the runtime, bundler, routing, and state APIs run on Vue 3.

The repository pins its runtime expectations in [`.java-version`](.java-version) and [`.nvmrc`](.nvmrc). Use a JDK 21 distribution and Node 24.18.0 (npm 11.16.0) before installing dependencies.

## Layout

```text
.
|- blog-springboot/    Spring Boot API (port 8090)
|- blog-vue/blog/      public blog Vite application
|- blog-vue/admin/     administrator Vite application
|- blog-mysql8.sql     MySQL 8 schema and sample data
`- AGENTS.md           contribution instructions
```

## Local setup

1. Create a dedicated local MySQL database and import the schema. The script drops and recreates its tables, so never import it into production.

   ```bash
   mysql -u <user> -p <database> < blog-mysql8.sql
   ```

   Existing installations are upgraded automatically by Flyway when the API starts. The first run baselines the legacy schema at version `0` and applies versioned migrations from `blog-springboot/src/main/resources/db/migration`; do not manually import the destructive seed file.

2. Configure local services with environment variables or an ignored `application-local.yml`. Start from [`application-local.example.yml`](blog-springboot/src/main/resources/application-local.example.yml); the committed `application.yml` intentionally contains no credentials. For local OAuth and captcha site keys, copy the relevant frontend `.env.example` file to `.env.local`.

3. Start the API:

   ```bash
   cd blog-springboot
   mvn spring-boot:run
   ```

   The API listens on `http://localhost:8090` by default. Local uploads are served from `http://localhost:8090/uploads/`.

   A new database contains roles and safe defaults but no hard-coded administrator credentials. To create the first administrator, set `BOOTSTRAP_ADMIN_ENABLED=true`, provide `BOOTSTRAP_ADMIN_USERNAME` and a non-placeholder `BOOTSTRAP_ADMIN_PASSWORD` of at least 12 characters, then start the API once. Disable the bootstrap flag after the account has been created; subsequent starts are idempotent for the same email address.

4. Install and start either frontend:

   ```bash
   cd blog-vue/blog       # or blog-vue/admin
   npm ci
   npm run dev
   ```

   Use a second port when running both applications, for example `npm run dev -- --port 8081` in `blog-vue/admin`. Both Vite configurations proxy `/api` to the backend and remove the `/api` prefix.

## Verification

```bash
# API, from blog-springboot
mvn test
mvn package

# Each frontend, from its own directory
npm run build
```

## Containers

For a complete local stack, copy [`.env.example`](.env.example) to `.env`, replace every placeholder, then run:

```bash
docker compose up --build
```

This starts MySQL, Redis, RabbitMQ, the API, the public site on `http://localhost:8080`, and the console on `http://localhost:8081`. The SQL initialization script runs only when the named MySQL volume is first created; it drops and recreates its tables, so never reuse a production data volume.

Uploads are persisted in the `uploads` volume and served through `/uploads/` on both frontend hosts. Redis state is persisted in the `redis-data` volume; use a managed Redis backup strategy in production.

Chat delivery, message recalls, and the online count are distributed through the Redis channel `ticastr:chat:events`. All API replicas must use the same Redis instance; WebSocket sessions are local to each replica and Redis fans events out to every replica. Online-session entries expire after 90 seconds without a heartbeat, so a failed node does not leave a permanent count behind.

HTTP sessions are stored in Redis under the configurable `ticastr:session` namespace. Authorization changes publish a Redis invalidation event so every API replica reloads its URL-role map. Short scheduled jobs use ownership-token locks, which prevents duplicate daily statistics when several API replicas are running.

The API exposes unauthenticated liveness/readiness checks at `/actuator/health` and `/actuator/health/**`; detailed health information remains private.

For metrics, set a non-empty `MONITORING_TOKEN` and scrape the API directly at `/actuator/prometheus` with the `X-Monitoring-Token` request header. The two frontend Nginx instances intentionally return `404` for this path, so a collector must reach the API through its private service network. The endpoint includes application-tagged JVM, process, HTTP server, datasource, and custom application metrics; HTTP request histograms include 100 ms, 500 ms, 1 s, and 5 s SLO buckets.

Configure the collector to send the `X-Monitoring-Token` header from its secret store. Keep the token out of this repository and do not substitute an `Authorization` header; the API intentionally accepts only the dedicated monitoring header.

## Development notes

- Keep frontend API calls relative (`/api/...`); do not hardcode `localhost:8090` in components.
- Backend follows `controller -> service -> dao`; update the corresponding mapper XML when adding persistence queries.
- Runtime configuration, CORS origins, and all service credentials are supplied through environment variables. Do not restore secrets to `application.yml`.
- See [AGENTS.md](AGENTS.md) for full collaboration, configuration, and commit rules.

## License

[Apache License 2.0](LICENSE)
