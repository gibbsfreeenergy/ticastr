ALTER TABLE `tb_menu`
    ADD COLUMN `code` varchar(80) NULL COMMENT '稳定菜单编码' AFTER `name`,
    ADD COLUMN `route_key` varchar(80) NULL COMMENT '前端路由注册表编码' AFTER `component`,
    ADD COLUMN `icon_key` varchar(80) NULL COMMENT '稳定图标编码' AFTER `icon`,
    ADD COLUMN `section` varchar(80) NULL COMMENT '稳定导航分组编码' AFTER `icon_key`;

UPDATE `tb_menu`
SET `route_key` = CASE
        WHEN `path` = '/' THEN 'home'
        WHEN `path` IN ('/articles', '/articles/*') THEN 'article'
        WHEN `path` = '/article-list' THEN 'articleList'
        WHEN `path` = '/categories' THEN 'category'
        WHEN `path` = '/tags' THEN 'tag'
        WHEN `path` = '/albums' THEN 'album'
        WHEN `path` = '/albums/:albumId' THEN 'photo'
        WHEN `path` = '/photos/delete' THEN 'albumDelete'
        WHEN `path` = '/comments' THEN 'comment'
        WHEN `path` = '/messages' THEN 'message'
        WHEN `path` = '/users' THEN 'user'
        WHEN `path` = '/online/users' THEN 'online'
        WHEN `path` = '/roles' THEN 'role'
        WHEN `path` = '/resources' THEN 'resource'
        WHEN `path` = '/menus' THEN 'menu'
        WHEN `path` = '/links' THEN 'friendLink'
        WHEN `path` = '/about' THEN 'about'
        WHEN `path` = '/operation/log' THEN 'operation'
        WHEN `path` = '/pages' THEN 'page'
        WHEN `path` = '/website' THEN 'website'
        WHEN `path` = '/setting' THEN 'setting'
        WHEN `path` IN ('/talks', '/talks/:talkId') THEN 'talk'
        WHEN `path` = '/talk-list' THEN 'talkList'
        WHEN `path` = '/article-submenu' THEN 'articleGroup'
        WHEN `path` = '/message-submenu' THEN 'messageGroup'
        WHEN `path` = '/system-submenu' THEN 'systemGroup'
        WHEN `path` = '/users-submenu' THEN 'userGroup'
        WHEN `path` = '/permission-submenu' THEN 'permissionGroup'
        WHEN `path` = '/album-submenu' THEN 'albumGroup'
        WHEN `path` = '/talk-submenu' THEN 'talkGroup'
        WHEN `path` = '/log-submenu' THEN 'logGroup'
        ELSE CONCAT('menu-', `id`)
    END
WHERE `route_key` IS NULL OR `route_key` = '';

UPDATE `tb_menu`
SET `code` = `route_key`
WHERE `code` IS NULL OR `code` = '';

CREATE TEMPORARY TABLE `tmp_menu_duplicate_codes` (
    `code` varchar(80) NOT NULL,
    `keeper_id` int NOT NULL,
    PRIMARY KEY (`code`)
);

INSERT INTO `tmp_menu_duplicate_codes` (`code`, `keeper_id`)
SELECT `code`, MIN(`id`)
FROM `tb_menu`
GROUP BY `code`
HAVING COUNT(*) > 1;

UPDATE `tb_menu` menu
JOIN `tmp_menu_duplicate_codes` duplicate_codes ON duplicate_codes.`code` = menu.`code`
SET menu.`code` = CONCAT(menu.`code`, '.', menu.`id`)
WHERE menu.`id` <> duplicate_codes.`keeper_id`;

DROP TEMPORARY TABLE `tmp_menu_duplicate_codes`;

UPDATE `tb_menu`
SET `section` = CASE
        WHEN `route_key` = 'home' THEN 'workspace'
        WHEN `route_key` REGEXP '^(article|category|tag|album|photo|talk)' THEN 'content'
        WHEN `route_key` REGEXP '^(comment|message|user|online)' THEN 'community'
        ELSE 'settings'
    END
WHERE `section` IS NULL OR `section` = '';

UPDATE `tb_menu`
SET `icon_key` = CASE
        WHEN `route_key` = 'home' THEN 'home'
        WHEN `route_key` REGEXP '^article' THEN 'pen'
        WHEN `route_key` IN ('articleList', 'talkList', 'menu') THEN 'list'
        WHEN `route_key` REGEXP '^(category|album|photo)' THEN 'folder'
        WHEN `route_key` = 'tag' THEN 'tag'
        WHEN `route_key` REGEXP '^talk' THEN 'bubble'
        WHEN `route_key` = 'comment' THEN 'comment'
        WHEN `route_key` = 'message' THEN 'message'
        WHEN `route_key` REGEXP '^user' THEN 'users'
        WHEN `route_key` = 'online' THEN 'user'
        WHEN `route_key` REGEXP '^(role|permission)' THEN 'shield'
        WHEN `route_key` = 'resource' THEN 'code'
        WHEN `route_key` = 'website' THEN 'globe'
        WHEN `route_key` = 'page' THEN 'page'
        WHEN `route_key` = 'friendLink' THEN 'link'
        WHEN `route_key` = 'about' THEN 'info'
        WHEN `route_key` REGEXP '^(operation|log)' THEN 'history'
        WHEN `route_key` REGEXP '^(setting|system)' THEN 'settings'
        ELSE 'grid'
    END
WHERE `icon_key` IS NULL OR `icon_key` = '';

CREATE INDEX `idx_menu_code` ON `tb_menu` (`code`);
