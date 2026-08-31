# AGENTS.md

## Project overview

`ticastr` is a personal blog system with one Spring Boot API and two Vue single-page applications:

- `blog-springboot/`: Java 21 / Spring Boot 4.1 API, default port `8090`.
- `blog-vue/blog/`: public blog, Vue 3 / Vite, default port `8080`.
- `blog-vue/admin/`: administrator console, Vue 3 / Vite, default port `8081`.
- `database/`: database initialization and migration notes; Flyway owns the schema.

Both frontend development servers proxy `/api` to `http://localhost:8090` and remove the `/api` prefix. The production containers use the same reverse-proxy rule.

## Development and verification

Confirm the actual commands in the relevant `package.json` or `pom.xml` before changing a module.

| Goal | Directory | Command |
| --- | --- | --- |
| Run the API | `blog-springboot` | `mvn spring-boot:run` |
| Test the API | `blog-springboot` | `mvn test` |
| Package the API | `blog-springboot` | `mvn package` |
| Run the public app | `blog-vue/blog` | `npm ci`, then `npm run dev` |
| Build the public app | `blog-vue/blog` | `npm run build` |
| Run the admin app | `blog-vue/admin` | `npm ci`, then `npm run dev -- --port 8081` |
| Build the admin app | `blog-vue/admin` | `npm run build` |

Use JDK 21 and Node 24.18.0 / npm 11.16.0, as pinned by `.java-version` and `.nvmrc`. When a change affects multiple modules, run the closest available verification for each affected module.

## Code boundaries and conventions

- Backend code follows `controller -> service -> dao`; controller classes are in `blog-springboot/src/main/java/com/wzh/blog/controller`, MyBatis mappers are in `src/main/resources/mapper`. Keep DAO interfaces and mapper XML in sync.
- Keep backend entities, DTOs and VOs distinct. Do not replace API input/output types with persistence entities unless nearby code already follows that pattern.
- Public routes are in `blog-vue/blog/src/router/index.js`. The administrator console has only its static login route in `blog-vue/admin/src/router/index.js`; other routes are driven by API menu data.
- Keep frontend requests relative (`/api/...`) so development and reverse-proxy deployments both work. Do not hardcode `localhost:8090` in business components.
- Preserve the existing Vue Options API style, double quotes and directory responsibilities (`views`, `components`, `store`, `assets`).
- Do not commit `target`, `dist`, `node_modules`, IDE settings, `.env`, local configuration, or credentials.

## Configuration and data safety

- `application.yml` contains only environment-variable placeholders and safe defaults. Put local overrides in the ignored `application-local.yml` or environment variables; start from `application-local.example.yml`. Frontend public OAuth/captcha values use `VITE_*` variables and the frontend `.env.example` files.
- Never commit passwords, tokens, private keys, OAuth secrets, or production endpoints. Rotate any secret that was committed previously.
- Flyway owns schema initialization and upgrades. Never import an unreviewed SQL export into a shared or production database.
- `compose.yaml` is for local integration environments. Copy `.env.example` to `.env` and set non-placeholder values before using it.

## Before committing

1. Inspect `git diff --check` and `git diff` for unrelated changes and secrets.
2. Run the closest test, build, or package command for affected modules.
3. Update README or relevant instructions when setup, configuration, routes, or initialization changes.
4. Stage only task-related files and use a concise, descriptive commit message.
