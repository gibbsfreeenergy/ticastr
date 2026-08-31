CREATE TABLE tb_storage_bootstrap_state (
    id tinyint NOT NULL,
    legacy_import_completed tinyint(1) NOT NULL DEFAULT 0,
    legacy_active_provider varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
    completed_at datetime NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_storage_bootstrap_state_singleton CHECK (id = 1)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '存储配置迁移状态';

INSERT INTO tb_storage_bootstrap_state (id, legacy_import_completed, legacy_active_provider, completed_at)
VALUES (1, 0, NULL, NULL);

UPDATE tb_storage_bootstrap_state
SET legacy_active_provider = (
    SELECT active_provider
    FROM tb_storage_provider_config
    WHERE id = 1
    LIMIT 1
)
WHERE id = 1;

ALTER TABLE tb_storage_provider_config
    DROP CHECK ck_storage_provider_config_singleton,
    ADD COLUMN config_name varchar(100) NOT NULL DEFAULT '本地默认配置',
    ADD COLUMN provider varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'local',
    ADD COLUMN endpoint varchar(512) NULL,
    ADD COLUMN bucket varchar(255) NULL,
    ADD COLUMN region varchar(128) NULL,
    ADD COLUMN local_root varchar(1024) NULL,
    ADD COLUMN public_url varchar(1024) NOT NULL DEFAULT '/uploads/',
    ADD COLUMN access_key_id_ciphertext text NULL,
    ADD COLUMN access_key_secret_ciphertext text NULL,
    ADD COLUMN is_active tinyint NOT NULL DEFAULT 0,
    ADD COLUMN config_source varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN last_validation_status varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'NEVER',
    ADD COLUMN last_validation_at datetime NULL,
    ADD COLUMN last_validation_message varchar(1000) NULL,
    ADD COLUMN usage_status varchar(16) CHARACTER SET ascii NOT NULL DEFAULT 'NEVER',
    ADD COLUMN usage_object_count bigint NULL,
    ADD COLUMN usage_bytes bigint NULL,
    ADD COLUMN usage_last_modified datetime NULL,
    ADD COLUMN usage_checked_at datetime NULL,
    ADD COLUMN usage_error varchar(1000) NULL,
    ADD COLUMN created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN legacy_source_provider varchar(16) CHARACTER SET ascii GENERATED ALWAYS AS
        (CASE WHEN config_source = 'LEGACY_ENV' THEN provider ELSE NULL END) STORED,
    ADD COLUMN active_marker tinyint GENERATED ALWAYS AS
        (CASE WHEN is_active = 1 THEN 1 ELSE NULL END) STORED,
    ADD CONSTRAINT ck_storage_provider_config_provider
        CHECK (provider IN ('local', 'cos', 'oss', 'tos')),
    ADD UNIQUE KEY uk_storage_provider_config_legacy_source (legacy_source_provider),
    ADD UNIQUE KEY uk_storage_provider_config_active (active_marker);

UPDATE tb_storage_provider_config
SET config_name = '本地默认配置',
    provider = 'local',
    local_root = 'uploads',
    public_url = '/uploads/',
    is_active = 1,
    config_source = 'DEFAULT',
    last_validation_status = 'NEVER',
    usage_status = 'NEVER',
    created_at = COALESCE(updated_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE id = 1;

ALTER TABLE tb_storage_provider_config
    DROP COLUMN active_provider,
    MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE tb_content_asset
    DROP INDEX uk_content_asset_provider_key,
    ADD COLUMN storage_config_id bigint NULL AFTER provider,
    ADD KEY idx_content_asset_storage_config (storage_config_id),
    ADD UNIQUE KEY uk_content_asset_config_key (storage_config_id, object_key),
    ADD CONSTRAINT fk_content_asset_storage_config
        FOREIGN KEY (storage_config_id) REFERENCES tb_storage_provider_config (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE tb_media_asset
    ADD COLUMN storage_config_id bigint NULL AFTER storage_mode,
    ADD KEY idx_media_asset_storage_config (storage_config_id),
    ADD CONSTRAINT fk_media_asset_storage_config
        FOREIGN KEY (storage_config_id) REFERENCES tb_storage_provider_config (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置列表', '/admin/storage/configs', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置详情', '/admin/storage/configs/*', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置创建', '/admin/storage/configs', 'POST', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置更新', '/admin/storage/configs/*', 'PUT', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置删除', '/admin/storage/configs/*', 'DELETE', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置验证', '/admin/storage/configs/*/validate', 'POST', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置启用', '/admin/storage/configs/*/activate', 'POST', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储用量刷新', '/admin/storage/configs/*/usage', 'POST', 0, 0, NOW(), NOW());
