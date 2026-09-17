-- Finalize the development-only simplification of the blog schema.
-- Historical migrations remain immutable; this migration removes their
-- retired tables, columns, permissions and data from the live database.

-- Keep only the administrator account and its profile. Public user accounts
-- existed only for the removed community and social features.
DELETE FROM tb_user_info
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_user_role user_role
    JOIN tb_role role ON role.id = user_role.role_id
    WHERE user_role.user_id = tb_user_info.id
      AND role.role_label = 'admin'
);

DELETE FROM tb_user_role
WHERE user_id IS NULL
   OR role_id IS NULL
   OR NOT EXISTS (SELECT 1 FROM tb_user_info WHERE tb_user_info.id = tb_user_role.user_id)
   OR NOT EXISTS (
       SELECT 1
       FROM tb_role
       WHERE tb_role.id = tb_user_role.role_id
         AND tb_role.role_label = 'admin'
   );

DELETE FROM tb_role
WHERE role_label <> 'admin';

-- Remove all legacy route permissions before replacing the route catalog.
DELETE FROM tb_role_menu;
DELETE FROM tb_role_resource;
DELETE FROM tb_menu;
DELETE FROM tb_resource;

-- The administrator console contains only the retained editing/configuration
-- surface. The article detail route is hidden but needed for editing drafts.
INSERT INTO tb_menu
    (name, code, path, component, route_key, icon, icon_key, section,
     create_time, update_time, order_num, parent_id, is_hidden)
VALUES
    ('首页', 'home', '/', 'Layout', 'home', 'home', 'home', 'workspace', NOW(), NOW(), 1, NULL, 0),
    ('文章管理', 'articleGroup', '/article-submenu', 'Layout', 'articleGroup', 'pen', 'pen', 'content', NOW(), NOW(), 10, NULL, 0),
    ('关于我', 'about', '/about', 'Layout', 'about', 'info', 'info', 'content', NOW(), NOW(), 20, NULL, 0),
    ('页面管理', 'page', '/pages', 'Layout', 'page', 'file', 'page', 'settings', NOW(), NOW(), 30, NULL, 0),
    ('网站管理', 'website', '/website', 'Layout', 'website', 'globe', 'globe', 'settings', NOW(), NOW(), 31, NULL, 0),
    ('个人中心', 'setting', '/setting', 'Layout', 'setting', 'settings', 'settings', 'settings', NOW(), NOW(), 32, NULL, 0),
    ('媒体存储', 'storage', '/storage', 'Layout', 'storage', 'folder', 'folder', 'settings', NOW(), NOW(), 33, NULL, 0);

INSERT INTO tb_menu
    (name, code, path, component, route_key, icon, icon_key, section,
     create_time, update_time, order_num, parent_id, is_hidden)
SELECT child.name, child.code, child.path, child.component, child.route_key,
       child.icon, child.icon_key, child.section, NOW(), NOW(), child.order_num,
       parent.id, child.is_hidden
FROM (
    SELECT '文章列表' AS name, 'articleList' AS code, '/articles' AS path,
           '/article/ArticleList.vue' AS component, 'articleList' AS route_key,
           'file' AS icon, 'file' AS icon_key, 'content' AS section,
           1 AS order_num, 0 AS is_hidden
    UNION ALL
    SELECT '文章编辑', 'article', '/articles/:articleId', '/article/Article.vue',
           'article', 'pen', 'pen', 'content', 2, 1
) child
JOIN tb_menu parent ON parent.code = 'articleGroup';

INSERT INTO tb_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM tb_role role
JOIN tb_menu menu
WHERE role.role_label = 'admin';

-- Rebuild the dynamic URL permission catalog so removed endpoints fail closed
-- instead of remaining reachable through stale database rows.
INSERT INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('Public API', NULL, NULL, NULL, 1, NOW(), NOW()),
       ('Admin API', NULL, NULL, NULL, 0, NOW(), NOW());

INSERT INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
SELECT seed.resource_name, seed.url, seed.request_method, parent.id, 1, NOW(), NOW()
FROM (
    SELECT '查看博客首页' AS resource_name, '/' AS url, 'GET' AS request_method
    UNION ALL SELECT '查看关于我', '/about', 'GET'
    UNION ALL SELECT '查看首页文章', '/articles', 'GET'
    UNION ALL SELECT '查看文章归档', '/articles/archives', 'GET'
    UNION ALL SELECT '搜索文章', '/articles/search', 'GET'
    UNION ALL SELECT '查看文章详情', '/articles/*', 'GET'
    UNION ALL SELECT '读取文章内容', '/articles/*/content', 'GET'
) seed
JOIN tb_resource parent
  ON parent.resource_name = 'Public API'
 AND parent.parent_id IS NULL;

INSERT INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
SELECT seed.resource_name, seed.url, seed.request_method, parent.id, 0, NOW(), NOW()
FROM (
    SELECT '后台首页' AS resource_name, '/admin' AS url, 'GET' AS request_method
    UNION ALL SELECT '后台文章列表', '/admin/articles', 'GET'
    UNION ALL SELECT '创建文章', '/admin/articles', 'POST'
    UNION ALL SELECT '修改文章删除状态', '/admin/articles', 'PUT'
    UNION ALL SELECT '物理删除文章', '/admin/articles', 'DELETE'
    UNION ALL SELECT '后台文章详情', '/admin/articles/*', 'GET'
    UNION ALL SELECT '修改文章置顶', '/admin/articles/top', 'PUT'
    UNION ALL SELECT '上传文章图片', '/admin/articles/images', 'POST'
    UNION ALL SELECT '读取后台文章内容', '/admin/articles/*/content', 'GET'
    UNION ALL SELECT '保存文章内容', '/admin/articles/*/content', 'PUT'
    UNION ALL SELECT '查看文章版本', '/admin/articles/*/versions', 'GET'
    UNION ALL SELECT '恢复文章版本', '/admin/articles/*/versions/*/restore', 'POST'
    UNION ALL SELECT '后台页面列表', '/admin/pages', 'GET'
    UNION ALL SELECT '保存页面', '/admin/pages', 'POST'
    UNION ALL SELECT '获取网站配置', '/admin/website/config', 'GET'
    UNION ALL SELECT '更新网站配置', '/admin/website/config', 'PUT'
    UNION ALL SELECT '修改关于我', '/admin/about', 'PUT'
    UNION ALL SELECT '上传配置图片', '/admin/config/images', 'POST'
    UNION ALL SELECT '获取存储配置', '/admin/storage/configs', 'GET'
    UNION ALL SELECT '创建存储配置', '/admin/storage/configs', 'POST'
    UNION ALL SELECT '更新存储配置', '/admin/storage/configs/*', 'PUT'
    UNION ALL SELECT '删除存储配置', '/admin/storage/configs/*', 'DELETE'
    UNION ALL SELECT '验证存储配置', '/admin/storage/configs/*/validate', 'POST'
    UNION ALL SELECT '启用存储配置', '/admin/storage/configs/*/activate', 'POST'
    UNION ALL SELECT '刷新存储用量', '/admin/storage/configs/*/usage', 'POST'
    UNION ALL SELECT '修改管理员密码', '/admin/users/password', 'PUT'
    UNION ALL SELECT '更新管理员资料', '/users/info', 'PUT'
    UNION ALL SELECT '更新管理员头像', '/users/avatar', 'POST'
    UNION ALL SELECT '获取管理员菜单', '/admin/user/menus', 'GET'
) seed
JOIN tb_resource parent
  ON parent.resource_name = 'Admin API'
 AND parent.parent_id IS NULL;

INSERT INTO tb_role_resource (role_id, resource_id)
SELECT role.id, resource.id
FROM tb_role role
JOIN tb_resource resource
  ON resource.parent_id IS NOT NULL
 AND resource.is_anonymous = 0
WHERE role.role_label = 'admin';

-- Remove taxonomy and all community/interaction/telemetry data structures.
ALTER TABLE tb_article
    DROP FOREIGN KEY fk_article_category,
    DROP INDEX idx_article_category_public,
    DROP INDEX idx_article_public_order,
    DROP COLUMN category_id;

DROP TABLE IF EXISTS tb_article_tag;
DROP TABLE IF EXISTS tb_category;
DROP TABLE IF EXISTS tb_tag;
DROP TABLE IF EXISTS tb_article_like;
DROP TABLE IF EXISTS tb_article_engagement;
DROP TABLE IF EXISTS tb_comment;
DROP TABLE IF EXISTS tb_friend_link;
DROP TABLE IF EXISTS tb_message;
DROP TABLE IF EXISTS tb_photo;
DROP TABLE IF EXISTS tb_photo_album;
DROP TABLE IF EXISTS tb_talk;
DROP TABLE IF EXISTS tb_chat_record;
DROP TABLE IF EXISTS tb_site_visitor;
DROP TABLE IF EXISTS tb_unique_view;
DROP TABLE IF EXISTS tb_operation_log;

-- Only the three public page covers are still meaningful.
DELETE FROM tb_page
WHERE page_label NOT IN ('home', 'archive', 'about')
   OR page_label IS NULL;

-- About and website configuration are singleton documents in the retained
-- application. Remove duplicate legacy rows before continuing with id 1.
DELETE FROM tb_about
WHERE id <> 1;

DELETE FROM tb_website_config
WHERE id <> 1;

-- Keep only fields consumed by the retained website configuration DTO.
UPDATE tb_website_config
SET config = JSON_REMOVE(
        COALESCE(config, JSON_OBJECT()),
        '$.touristAvatar',
        '$.userAvatar',
        '$.socialLoginList',
        '$.isCommentReview',
        '$.isMessageReview',
        '$.isEmailNotice',
        '$.isReward',
        '$.isChatRoom',
        '$.isMusicPlayer',
        '$.websocketUrl'
    );

-- User login telemetry and the media source discriminator belonged to the
-- removed features and are no longer part of the application model.
ALTER TABLE tb_user_auth
    DROP COLUMN ip_address,
    DROP COLUMN ip_source,
    DROP COLUMN last_login_time;

ALTER TABLE tb_media_asset
    DROP INDEX idx_media_asset_source_status,
    DROP COLUMN source_type;

-- Drop queued events that can no longer be handled after feature removal.
DELETE FROM tb_outbox_event
WHERE event_type NOT IN ('ARTICLE_CONTENT_INDEX', 'MEDIA_DELETE');

-- Keep the shared media ledger intact. Article Markdown is stored in external
-- content assets, so its embedded image references cannot be discovered by a
-- relational query without risking deletion of images still used by articles.
