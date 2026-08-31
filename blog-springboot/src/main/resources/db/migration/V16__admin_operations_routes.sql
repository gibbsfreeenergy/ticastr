INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储 provider 状态', '/admin/storage/providers', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('对象存储 provider 验证', '/admin/storage/providers/*/validate', 'POST', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('可靠事件列表', '/admin/outbox', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('可靠事件统计', '/admin/outbox/metrics', 'GET', 0, 0, NOW(), NOW());

INSERT IGNORE INTO tb_resource (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
VALUES ('可靠事件重试', '/admin/outbox/*/retry', 'POST', 0, 0, NOW(), NOW());
