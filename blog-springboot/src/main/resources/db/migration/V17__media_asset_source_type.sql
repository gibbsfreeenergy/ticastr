ALTER TABLE tb_media_asset
    ADD COLUMN source_type varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci
        NOT NULL DEFAULT 'MEDIA' AFTER storage_mode,
    ADD INDEX idx_media_asset_source_status (source_type, status, updated_at);
