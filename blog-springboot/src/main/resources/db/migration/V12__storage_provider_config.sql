CREATE TABLE tb_storage_provider_config (
    id tinyint NOT NULL,
    active_provider varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    updated_at datetime NOT NULL,
    updated_by int NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_storage_provider_config_singleton CHECK (id = 1),
    CONSTRAINT fk_storage_provider_config_user
        FOREIGN KEY (updated_by) REFERENCES tb_user_info (id) ON DELETE SET NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '当前对象存储 provider';

INSERT INTO tb_storage_provider_config (id, active_provider, updated_at)
VALUES (1, 'local', CURRENT_TIMESTAMP);
