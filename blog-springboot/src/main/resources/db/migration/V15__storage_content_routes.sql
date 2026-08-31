INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('文章内容读取', '/articles/*/content', 'GET', 0, 1, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('文章内容管理', '/admin/articles/*/content', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('文章内容写入', '/admin/articles/*/content', 'PUT', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储配置', '/admin/storage/provider', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储切换', '/admin/storage/provider', 'PUT', 0, 0, NOW(), NOW());
