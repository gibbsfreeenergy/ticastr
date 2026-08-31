CREATE TABLE `tb_media_asset` (
    `asset_id` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '引用的SHA-256',
    `asset_reference` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对外访问引用',
    `object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'provider对象key',
    `storage_mode` varchar(20) NOT NULL COMMENT 'local oss cos tos',
    `status` varchar(20) NOT NULL COMMENT 'ACTIVE DELETING DELETED DELETE_FAILED',
    `created_at` datetime NOT NULL,
    `updated_at` datetime NOT NULL,
    `deleted_at` datetime NULL,
    `last_error` varchar(1000) NULL,
    PRIMARY KEY (`asset_id`),
    INDEX `idx_media_asset_status` (`status`, `updated_at`),
    INDEX `idx_media_asset_reference` (`asset_reference`(191))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '外部媒体资产台账';
