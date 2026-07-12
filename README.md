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

2. Configure local service endpoints in `blog-springboot/src/main/resources/application.yml`. Keep real passwords, tokens, and private keys out of Git.

3. Start the API:

   ```bash
   cd blog-springboot
   mvn spring-boot:run
   ```

   The API listens on `http://localhost:8090` by default.

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

There are no backend test sources at present. A production database import and all external services are required for a complete end-to-end runtime check.

## Development notes

- Keep frontend API calls relative (`/api/...`); do not hardcode `localhost:8090` in components.
- Backend follows `controller -> service -> dao`; update the corresponding mapper XML when adding persistence queries.
- See [AGENTS.md](AGENTS.md) for full collaboration, configuration, and commit rules.

## License

[Apache License 2.0](LICENSE)
