-- D1 schema for the retained ticastr blog/admin domain.
--
-- This is the final schema represented by the 28 MySQL Flyway migrations.
-- Flyway's bookkeeping table is intentionally omitted because D1 tracks its
-- own migrations in d1_migrations.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS tb_about (
  id INTEGER NOT NULL PRIMARY KEY,
  content TEXT NOT NULL,
  create_time TEXT NOT NULL,
  update_time TEXT
);
CREATE TABLE IF NOT EXISTS tb_user_info (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT,
  nickname TEXT NOT NULL,
  avatar TEXT NOT NULL DEFAULT '',
  intro TEXT,
  web_site TEXT,
  is_disable INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT
);

CREATE TABLE IF NOT EXISTS tb_role (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  role_name TEXT NOT NULL,
  role_label TEXT NOT NULL,
  is_disable INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT,
  UNIQUE (role_name),
  UNIQUE (role_label)
);

CREATE TABLE IF NOT EXISTS tb_storage_bootstrap_state (
  id INTEGER NOT NULL PRIMARY KEY,
  legacy_import_completed INTEGER NOT NULL DEFAULT 0,
  legacy_active_provider TEXT,
  completed_at TEXT,
  CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS tb_storage_provider_config (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  updated_at TEXT NOT NULL,
  updated_by INTEGER,
  config_name TEXT NOT NULL DEFAULT '本地默认配置',
  provider TEXT NOT NULL DEFAULT 'local',
  endpoint TEXT,
  bucket TEXT,
  region TEXT,
  local_root TEXT,
  public_url TEXT NOT NULL DEFAULT '/uploads/',
  access_key_id_ciphertext TEXT,
  access_key_secret_ciphertext TEXT,
  is_active INTEGER NOT NULL DEFAULT 0,
  config_source TEXT NOT NULL DEFAULT 'DEFAULT',
  last_validation_status TEXT NOT NULL DEFAULT 'NEVER',
  last_validation_at TEXT,
  last_validation_message TEXT,
  usage_status TEXT NOT NULL DEFAULT 'NEVER',
  usage_object_count INTEGER,
  usage_bytes INTEGER,
  usage_last_modified TEXT,
  usage_checked_at TEXT,
  usage_error TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (provider IN ('local', 'cos', 'oss', 'tos')),
  FOREIGN KEY (updated_by) REFERENCES tb_user_info (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_storage_provider_config_legacy_source
  ON tb_storage_provider_config (provider)
  WHERE config_source = 'LEGACY_ENV';

CREATE UNIQUE INDEX IF NOT EXISTS uk_storage_provider_config_active
  ON tb_storage_provider_config (is_active)
  WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS fk_storage_provider_config_user
  ON tb_storage_provider_config (updated_by);

CREATE TABLE IF NOT EXISTS tb_article (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  article_cover TEXT,
  article_title TEXT NOT NULL,
  type INTEGER NOT NULL DEFAULT 0,
  original_url TEXT,
  is_top INTEGER NOT NULL DEFAULT 0,
  is_delete INTEGER NOT NULL DEFAULT 0,
  status INTEGER NOT NULL DEFAULT 1,
  create_time TEXT NOT NULL,
  update_time TEXT,
  content_asset_id TEXT,
  FOREIGN KEY (content_asset_id) REFERENCES tb_content_asset (asset_id)
);

CREATE INDEX IF NOT EXISTS idx_article_content_asset
  ON tb_article (content_asset_id);

CREATE INDEX IF NOT EXISTS idx_article_public_cursor
  ON tb_article (is_delete, status, create_time, id);

CREATE TABLE IF NOT EXISTS tb_content_asset (
  asset_id TEXT NOT NULL PRIMARY KEY,
  article_id INTEGER NOT NULL,
  provider TEXT NOT NULL,
  storage_config_id INTEGER,
  object_key TEXT NOT NULL,
  content_type TEXT NOT NULL,
  format TEXT NOT NULL,
  version INTEGER NOT NULL,
  checksum TEXT,
  size_bytes INTEGER,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  last_error TEXT,
  UNIQUE (article_id, version),
  UNIQUE (storage_config_id, object_key),
  FOREIGN KEY (article_id) REFERENCES tb_article (id),
  FOREIGN KEY (storage_config_id) REFERENCES tb_storage_provider_config (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_content_asset_article_status
  ON tb_content_asset (article_id, status);

CREATE INDEX IF NOT EXISTS idx_content_asset_storage_config
  ON tb_content_asset (storage_config_id);

CREATE TABLE IF NOT EXISTS tb_media_asset (
  asset_id TEXT NOT NULL PRIMARY KEY,
  asset_reference TEXT NOT NULL,
  object_key TEXT NOT NULL,
  storage_mode TEXT NOT NULL,
  storage_config_id INTEGER,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  last_error TEXT,
  FOREIGN KEY (storage_config_id) REFERENCES tb_storage_provider_config (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_media_asset_status
  ON tb_media_asset (status, updated_at);

CREATE INDEX IF NOT EXISTS idx_media_asset_reference
  ON tb_media_asset (asset_reference);

CREATE INDEX IF NOT EXISTS idx_media_asset_storage_config
  ON tb_media_asset (storage_config_id);

CREATE TABLE IF NOT EXISTS tb_menu (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  code TEXT,
  path TEXT NOT NULL,
  component TEXT NOT NULL,
  route_key TEXT,
  icon TEXT NOT NULL,
  icon_key TEXT,
  section TEXT,
  create_time TEXT NOT NULL,
  update_time TEXT,
  order_num INTEGER NOT NULL,
  parent_id INTEGER,
  is_hidden INTEGER NOT NULL DEFAULT 0,
  UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS tb_outbox_event (
  event_id TEXT NOT NULL PRIMARY KEY,
  event_type TEXT NOT NULL,
  event_version INTEGER NOT NULL DEFAULT 1,
  aggregate_id TEXT,
  payload TEXT NOT NULL,
  trace_id TEXT,
  status TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TEXT NOT NULL,
  claimed_at TEXT,
  enqueued_at TEXT,
  processing_started_at TEXT,
  created_at TEXT NOT NULL,
  published_at TEXT,
  processed_at TEXT,
  last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch_v2
  ON tb_outbox_event (status, next_attempt_at, claimed_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
  ON tb_outbox_event (status, created_at, event_id);

CREATE TABLE IF NOT EXISTS tb_page (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  page_name TEXT NOT NULL,
  page_label TEXT,
  page_cover TEXT NOT NULL,
  create_time TEXT NOT NULL,
  update_time TEXT,
  UNIQUE (page_label)
);

CREATE TABLE IF NOT EXISTS tb_resource (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  resource_name TEXT NOT NULL,
  url TEXT,
  request_method TEXT,
  parent_id INTEGER,
  is_anonymous INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT,
  UNIQUE (url, request_method)
);

CREATE TABLE IF NOT EXISTS tb_role_menu (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  role_id INTEGER NOT NULL,
  menu_id INTEGER NOT NULL,
  UNIQUE (role_id, menu_id),
  FOREIGN KEY (menu_id) REFERENCES tb_menu (id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES tb_role (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS fk_role_menu_menu
  ON tb_role_menu (menu_id);

CREATE TABLE IF NOT EXISTS tb_role_resource (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  role_id INTEGER NOT NULL,
  resource_id INTEGER NOT NULL,
  UNIQUE (role_id, resource_id),
  FOREIGN KEY (resource_id) REFERENCES tb_resource (id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES tb_role (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS fk_role_resource_resource
  ON tb_role_resource (resource_id);

CREATE TABLE IF NOT EXISTS tb_user_auth (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_info_id INTEGER NOT NULL,
  username TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  login_type INTEGER NOT NULL,
  create_time TEXT NOT NULL,
  update_time TEXT,
  FOREIGN KEY (user_info_id) REFERENCES tb_user_info (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS fk_user_auth_user_info
  ON tb_user_auth (user_info_id);

CREATE TABLE IF NOT EXISTS tb_user_role (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  role_id INTEGER NOT NULL,
  UNIQUE (user_id, role_id),
  FOREIGN KEY (role_id) REFERENCES tb_role (id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES tb_user_info (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS fk_user_role_role
  ON tb_user_role (role_id);

CREATE TABLE IF NOT EXISTS tb_website_config (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  config TEXT,
  create_time TEXT NOT NULL,
  update_time TEXT
);
