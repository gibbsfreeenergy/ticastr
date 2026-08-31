CREATE TABLE tb_content_asset (
    asset_id char(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    article_id int NOT NULL,
    provider varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    object_key varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    content_type varchar(100) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    format varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    version int NOT NULL,
    checksum char(64) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
    size_bytes bigint NULL,
    status varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    created_at datetime NOT NULL,
    updated_at datetime NOT NULL,
    deleted_at datetime NULL,
    last_error varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_content_asset_article_version (article_id, version),
    UNIQUE KEY uk_content_asset_provider_key (provider, object_key),
    KEY idx_content_asset_article_status (article_id, status),
    CONSTRAINT fk_content_asset_article FOREIGN KEY (article_id) REFERENCES tb_article (id)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章内容对象资产';

ALTER TABLE tb_article
    ADD COLUMN content_asset_id char(36) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
    ADD KEY idx_article_content_asset (content_asset_id),
    ADD CONSTRAINT fk_article_content_asset
        FOREIGN KEY (content_asset_id) REFERENCES tb_content_asset (asset_id);

ALTER TABLE tb_article DROP COLUMN article_content;
