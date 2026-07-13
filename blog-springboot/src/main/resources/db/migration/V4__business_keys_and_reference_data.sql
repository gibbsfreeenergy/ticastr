-- Merge duplicate route resources while preserving every role assignment.
INSERT IGNORE INTO `tb_role_resource` (`role_id`, `resource_id`)
SELECT role_resource.role_id, canonical.id
FROM `tb_role_resource` role_resource
JOIN `tb_resource` duplicate ON duplicate.id = role_resource.resource_id
JOIN `tb_resource` canonical
  ON canonical.url = duplicate.url
 AND canonical.request_method = duplicate.request_method
 AND canonical.id < duplicate.id
LEFT JOIN `tb_resource` earlier
  ON earlier.url = duplicate.url
 AND earlier.request_method = duplicate.request_method
 AND earlier.id < canonical.id
WHERE duplicate.url IS NOT NULL
  AND duplicate.request_method IS NOT NULL
  AND earlier.id IS NULL;

DELETE role_resource
FROM `tb_role_resource` role_resource
JOIN `tb_resource` duplicate ON duplicate.id = role_resource.resource_id
JOIN `tb_resource` canonical
  ON canonical.url = duplicate.url
 AND canonical.request_method = duplicate.request_method
 AND canonical.id < duplicate.id
WHERE duplicate.url IS NOT NULL AND duplicate.request_method IS NOT NULL;

DELETE duplicate
FROM `tb_resource` duplicate
JOIN `tb_resource` canonical
  ON canonical.url = duplicate.url
 AND canonical.request_method = duplicate.request_method
 AND canonical.id < duplicate.id
WHERE duplicate.url IS NOT NULL AND duplicate.request_method IS NOT NULL;

-- Preserve duplicate custom roles/pages by assigning stable, unique business keys.
UPDATE `tb_role` duplicate
JOIN `tb_role` canonical
  ON canonical.role_label = duplicate.role_label AND canonical.id < duplicate.id
SET duplicate.role_label = CONCAT(LEFT(duplicate.role_label, 40), '_', duplicate.id);

UPDATE `tb_role` duplicate
JOIN `tb_role` canonical
  ON canonical.role_name = duplicate.role_name AND canonical.id < duplicate.id
SET duplicate.role_name = CONCAT(LEFT(duplicate.role_name, 12), '_', duplicate.id);

UPDATE `tb_page` duplicate
JOIN `tb_page` canonical
  ON canonical.page_label = duplicate.page_label AND canonical.id < duplicate.id
SET duplicate.page_label = CONCAT(LEFT(duplicate.page_label, 12), '_', duplicate.id)
WHERE duplicate.page_label IS NOT NULL;

ALTER TABLE `tb_resource`
    ADD CONSTRAINT `uk_resource_route` UNIQUE (`url`, `request_method`);
ALTER TABLE `tb_role`
    ADD CONSTRAINT `uk_role_name` UNIQUE (`role_name`),
    ADD CONSTRAINT `uk_role_label` UNIQUE (`role_label`);
ALTER TABLE `tb_page`
    ADD CONSTRAINT `uk_page_label` UNIQUE (`page_label`);

INSERT INTO `tb_role` (`role_name`, `role_label`, `is_disable`, `create_time`)
SELECT '管理员', 'admin', 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tb_role` WHERE `role_label` = 'admin');

INSERT INTO `tb_role` (`role_name`, `role_label`, `is_disable`, `create_time`)
SELECT '用户', 'user', 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tb_role` WHERE `role_label` = 'user');

INSERT INTO `tb_website_config` (`id`, `config`, `create_time`)
SELECT 1,
       '{"websiteName":"个人博客","websiteAuthor":"网站作者","websiteIntro":"","websiteNotice":"","websiteCreateTime":"","websiteRecordNo":"","websiteAvatar":"","touristAvatar":"","userAvatar":"","socialLoginList":[],"socialUrlList":[],"isCommentReview":0,"isMessageReview":0,"isEmailNotice":0,"isReward":0,"isChatRoom":0,"isMusicPlayer":0,"websocketUrl":""}',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tb_website_config` WHERE `id` = 1);

INSERT INTO `tb_resource` (`resource_name`, `parent_id`, `is_anonymous`, `create_time`)
SELECT 'Public API', NULL, 1, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `tb_resource`
    WHERE `resource_name` = 'Public API' AND `parent_id` IS NULL
);

INSERT INTO `tb_resource`
    (`resource_name`, `url`, `request_method`, `parent_id`, `is_anonymous`, `create_time`)
SELECT seed.resource_name, seed.url, seed.request_method, parent.id, 1, NOW()
FROM (
    SELECT '查看博客信息' resource_name, '/' url, 'GET' request_method
    UNION ALL SELECT '查看关于我信息', '/about', 'GET'
    UNION ALL SELECT '根据相册查看照片', '/albums/*/photos', 'GET'
    UNION ALL SELECT '查看首页文章', '/articles', 'GET'
    UNION ALL SELECT '查看文章归档', '/articles/archives', 'GET'
    UNION ALL SELECT '根据条件查询文章', '/articles/condition', 'GET'
    UNION ALL SELECT '搜索文章', '/articles/search', 'GET'
    UNION ALL SELECT '根据id查看文章', '/articles/*', 'GET'
    UNION ALL SELECT '查看分类列表', '/categories', 'GET'
    UNION ALL SELECT '查询评论', '/comments', 'GET'
    UNION ALL SELECT '查询评论回复', '/comments/*/replies', 'GET'
    UNION ALL SELECT '查看友链列表', '/links', 'GET'
    UNION ALL SELECT '查看留言列表', '/messages', 'GET'
    UNION ALL SELECT '添加留言', '/messages', 'POST'
    UNION ALL SELECT '获取相册列表', '/photos/albums', 'GET'
    UNION ALL SELECT '用户注册', '/register', 'POST'
    UNION ALL SELECT '查询标签列表', '/tags', 'GET'
    UNION ALL SELECT '发送邮箱验证码', '/users/code', 'GET'
    UNION ALL SELECT 'QQ登录', '/users/oauth/qq', 'POST'
    UNION ALL SELECT '微博登录', '/users/oauth/weibo', 'POST'
    UNION ALL SELECT '修改密码', '/users/password', 'PUT'
    UNION ALL SELECT '上传语音', '/voice', 'POST'
    UNION ALL SELECT '上报访问信息', '/report', 'POST'
    UNION ALL SELECT '查看首页说说', '/home/talks', 'GET'
    UNION ALL SELECT '查看说说列表', '/talks', 'GET'
    UNION ALL SELECT '根据id查看说说', '/talks/*', 'GET'
) seed
JOIN `tb_resource` parent
  ON parent.resource_name = 'Public API' AND parent.parent_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `tb_resource` existing
    WHERE existing.url = seed.url AND existing.request_method = seed.request_method
);
