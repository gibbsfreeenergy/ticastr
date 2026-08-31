# Database assets

## Source of truth

Flyway migrations in `blog-springboot/src/main/resources/db/migration` are the only source of truth for schema creation and evolution.

The repository intentionally does not contain a full database export. Production backups, historical exports, user records, authentication material, operation logs, and environment metadata must stay outside the application source tree.

## Local initialization

Create an empty local database and start the API. Flyway creates and upgrades the schema automatically. For the first local administrator, set `BOOTSTRAP_ADMIN_ENABLED=true`, provide a valid `BOOTSTRAP_ADMIN_USERNAME`, and provide a non-placeholder `BOOTSTRAP_ADMIN_PASSWORD` with at least 12 characters. Disable the flag after the account is created.

`blog-springboot/src/test/resources/db/legacy-schema.sql` is a minimal, sanitized, test-only fixture. It exists only to verify the Flyway legacy-baseline path and must not be used as a production backup or application seed.

Never import an unreviewed SQL export into a shared or production database. Use a dedicated database and an approved backup/restore procedure for legacy migrations.
