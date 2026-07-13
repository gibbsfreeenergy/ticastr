SET @client_token_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tb_chat_record'
      AND column_name = 'client_token'
);
SET @add_client_token_sql := IF(
    @client_token_column_exists = 0,
    'ALTER TABLE `tb_chat_record` ADD COLUMN `client_token` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NULL DEFAULT NULL COMMENT ''anonymous client identity digest'' AFTER `ip_source`',
    'SELECT 1'
);
PREPARE add_client_token_statement FROM @add_client_token_sql;
EXECUTE add_client_token_statement;
DEALLOCATE PREPARE add_client_token_statement;

CREATE TABLE IF NOT EXISTS `tb_about` (
    `id` int NOT NULL COMMENT 'fixed primary key',
    `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'about page content',
    `create_time` datetime NOT NULL COMMENT 'creation time',
    `update_time` datetime NULL DEFAULT NULL COMMENT 'update time',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO `tb_about` (`id`, `content`, `create_time`)
SELECT 1, '', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tb_about` WHERE `id` = 1);

INSERT INTO `tb_resource` (`resource_name`, `url`, `request_method`, `parent_id`, `is_anonymous`, `create_time`, `update_time`)
SELECT 'Upload website configuration image', '/admin/config/images', 'POST', parent_resource.id, 0, NOW(), NULL
FROM `tb_resource` parent_resource
WHERE parent_resource.parent_id IS NULL
  AND parent_resource.resource_name = '博客信息模块'
  AND NOT EXISTS (
      SELECT 1 FROM `tb_resource`
      WHERE `url` = '/admin/config/images' AND `request_method` = 'POST'
  )
LIMIT 1;

INSERT INTO `tb_role_resource` (`role_id`, `resource_id`)
SELECT role.id, resource.id
FROM `tb_role` role
JOIN `tb_resource` resource
  ON resource.url = '/admin/config/images' AND resource.request_method = 'POST'
WHERE role.role_label = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `tb_role_resource`
      WHERE `role_id` = role.id AND `resource_id` = resource.id
  );
