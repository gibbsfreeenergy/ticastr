INSERT IGNORE INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES
    ('文章内容版本历史', '/admin/articles/*/versions', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES
    ('恢复文章内容版本', '/admin/articles/*/versions/*/restore', 'POST', 0, 0, NOW(), NOW());
