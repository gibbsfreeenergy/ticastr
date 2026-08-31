-- Minimal sanitized legacy fixture used only by DatabaseMigrationIntegrationTest.
-- It intentionally contains no user, credential, address, log, or production data.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_article` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT 'author',
  `category_id` int NULL DEFAULT NULL COMMENT 'category',
  `article_cover` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'cover',
  `article_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'title',
  `article_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'content',
  `type` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'type',
  `original_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'original url',
  `is_top` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'top',
  `is_delete` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'deleted',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'status',
  `create_time` datetime NOT NULL COMMENT 'created',
  `update_time` datetime NULL DEFAULT NULL COMMENT 'updated',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

INSERT INTO `tb_article`
  (`id`, `user_id`, `category_id`, `article_cover`, `article_title`, `article_content`,
   `type`, `original_url`, `is_top`, `is_delete`, `status`, `create_time`, `update_time`)
VALUES
  (54, 1, NULL, '/uploads/legacy-cover.png', 'Sanitized legacy fixture',
   '<p>Sanitized legacy migration fixture.</p>', 1, NULL, 0, 0, 1,
   '2024-01-01 00:00:00', NULL);
