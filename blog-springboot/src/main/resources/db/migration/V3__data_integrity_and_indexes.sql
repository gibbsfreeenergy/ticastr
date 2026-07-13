-- Consolidate duplicate taxonomy values before enforcing business keys.
UPDATE `tb_article` article
JOIN (
    SELECT duplicate.id duplicate_id, canonical.id canonical_id
    FROM `tb_category` duplicate
    JOIN `tb_category` canonical
      ON canonical.category_name = duplicate.category_name
     AND canonical.id < duplicate.id
    LEFT JOIN `tb_category` earlier
      ON earlier.category_name = duplicate.category_name
     AND earlier.id < canonical.id
    WHERE earlier.id IS NULL
) category_map ON article.category_id = category_map.duplicate_id
SET article.category_id = category_map.canonical_id;

DELETE duplicate
FROM `tb_category` duplicate
JOIN `tb_category` canonical
  ON canonical.category_name = duplicate.category_name
 AND canonical.id < duplicate.id;

UPDATE `tb_article_tag` article_tag
JOIN (
    SELECT duplicate.id duplicate_id, canonical.id canonical_id
    FROM `tb_tag` duplicate
    JOIN `tb_tag` canonical
      ON canonical.tag_name = duplicate.tag_name
     AND canonical.id < duplicate.id
    LEFT JOIN `tb_tag` earlier
      ON earlier.tag_name = duplicate.tag_name
     AND earlier.id < canonical.id
    WHERE earlier.id IS NULL
) tag_map ON article_tag.tag_id = tag_map.duplicate_id
SET article_tag.tag_id = tag_map.canonical_id;

DELETE duplicate
FROM `tb_tag` duplicate
JOIN `tb_tag` canonical
  ON canonical.tag_name = duplicate.tag_name
 AND canonical.id < duplicate.id;

-- Remove duplicate and orphaned association rows before adding constraints.
DELETE duplicate FROM `tb_article_tag` duplicate
JOIN `tb_article_tag` canonical
  ON canonical.article_id = duplicate.article_id
 AND canonical.tag_id = duplicate.tag_id
 AND canonical.id < duplicate.id;
DELETE association FROM `tb_article_tag` association
LEFT JOIN `tb_article` article ON article.id = association.article_id
LEFT JOIN `tb_tag` tag ON tag.id = association.tag_id
WHERE article.id IS NULL OR tag.id IS NULL;

DELETE duplicate FROM `tb_user_role` duplicate
JOIN `tb_user_role` canonical
  ON canonical.user_id = duplicate.user_id
 AND canonical.role_id = duplicate.role_id
 AND canonical.id < duplicate.id;
DELETE association FROM `tb_user_role` association
LEFT JOIN `tb_user_info` user_info ON user_info.id = association.user_id
LEFT JOIN `tb_role` role ON role.id = association.role_id
WHERE user_info.id IS NULL OR role.id IS NULL;

DELETE duplicate FROM `tb_role_menu` duplicate
JOIN `tb_role_menu` canonical
  ON canonical.role_id = duplicate.role_id
 AND canonical.menu_id = duplicate.menu_id
 AND canonical.id < duplicate.id;
DELETE association FROM `tb_role_menu` association
LEFT JOIN `tb_role` role ON role.id = association.role_id
LEFT JOIN `tb_menu` menu ON menu.id = association.menu_id
WHERE role.id IS NULL OR menu.id IS NULL;

DELETE duplicate FROM `tb_role_resource` duplicate
JOIN `tb_role_resource` canonical
  ON canonical.role_id = duplicate.role_id
 AND canonical.resource_id = duplicate.resource_id
 AND canonical.id < duplicate.id;
DELETE association FROM `tb_role_resource` association
LEFT JOIN `tb_role` role ON role.id = association.role_id
LEFT JOIN `tb_resource` resource ON resource.id = association.resource_id
WHERE role.id IS NULL OR resource.id IS NULL;

ALTER TABLE `tb_category`
    ADD CONSTRAINT `uk_category_name` UNIQUE (`category_name`);
ALTER TABLE `tb_tag`
    ADD CONSTRAINT `uk_tag_name` UNIQUE (`tag_name`);
ALTER TABLE `tb_article_tag`
    ADD CONSTRAINT `uk_article_tag` UNIQUE (`article_id`, `tag_id`),
    ADD CONSTRAINT `fk_article_tag_article` FOREIGN KEY (`article_id`) REFERENCES `tb_article` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_article_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tb_tag` (`id`) ON DELETE CASCADE;
ALTER TABLE `tb_user_role`
    MODIFY `user_id` int NOT NULL,
    MODIFY `role_id` int NOT NULL,
    ADD CONSTRAINT `uk_user_role` UNIQUE (`user_id`, `role_id`),
    ADD CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user_info` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`id`) ON DELETE CASCADE;
ALTER TABLE `tb_role_menu`
    MODIFY `role_id` int NOT NULL,
    MODIFY `menu_id` int NOT NULL,
    ADD CONSTRAINT `uk_role_menu` UNIQUE (`role_id`, `menu_id`),
    ADD CONSTRAINT `fk_role_menu_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_role_menu_menu` FOREIGN KEY (`menu_id`) REFERENCES `tb_menu` (`id`) ON DELETE CASCADE;
ALTER TABLE `tb_role_resource`
    MODIFY `role_id` int NOT NULL,
    MODIFY `resource_id` int NOT NULL,
    ADD CONSTRAINT `uk_role_resource` UNIQUE (`role_id`, `resource_id`),
    ADD CONSTRAINT `fk_role_resource_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_role_resource_resource` FOREIGN KEY (`resource_id`) REFERENCES `tb_resource` (`id`) ON DELETE CASCADE;

ALTER TABLE `tb_article`
    ADD INDEX `idx_article_public_order` (`is_delete`, `status`, `is_top`, `id`),
    ADD INDEX `idx_article_category_public` (`category_id`, `is_delete`, `status`, `id`);
ALTER TABLE `tb_comment`
    ADD INDEX `idx_comment_topic_page` (`topic_id`, `type`, `is_review`, `parent_id`, `id`),
    ADD INDEX `idx_comment_reply_page` (`parent_id`, `is_review`, `id`);
ALTER TABLE `tb_talk`
    ADD INDEX `idx_talk_public_order` (`status`, `is_top`, `id`);
ALTER TABLE `tb_chat_record`
    ADD INDEX `idx_chat_record_created` (`create_time`);
ALTER TABLE `tb_operation_log`
    ADD INDEX `idx_operation_log_created` (`create_time`),
    ADD INDEX `idx_operation_log_user_created` (`user_id`, `create_time`),
    ADD INDEX `idx_operation_log_module_created` (`opt_module`, `create_time`);
ALTER TABLE `tb_unique_view`
    ADD COLUMN `view_date` date GENERATED ALWAYS AS (DATE(`create_time`)) STORED;

DELETE duplicate FROM `tb_unique_view` duplicate
JOIN `tb_unique_view` canonical
  ON canonical.view_date = duplicate.view_date
 AND canonical.id < duplicate.id;

ALTER TABLE `tb_unique_view`
    ADD CONSTRAINT `uk_unique_view_date` UNIQUE (`view_date`);
