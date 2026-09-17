-- Keep article content and version resources aligned with the article editor.
INSERT IGNORE INTO tb_role_resource (role_id, resource_id)
SELECT role.id, resource.id
FROM tb_role role
JOIN tb_resource resource
  ON (resource.url = '/admin/articles/*/content' AND resource.request_method = 'GET')
      OR (resource.url = '/admin/articles/*/versions' AND resource.request_method = 'GET')
WHERE role.role_label IN ('admin', 'test');

INSERT IGNORE INTO tb_role_resource (role_id, resource_id)
SELECT role.id, resource.id
FROM tb_role role
JOIN tb_resource resource
  ON (resource.url = '/admin/articles/*/content' AND resource.request_method = 'PUT')
      OR (resource.url = '/admin/articles/*/versions/*/restore' AND resource.request_method = 'POST')
      OR (resource.url IN ('/admin/storage/provider', '/admin/storage/providers')
          AND resource.request_method IN ('GET', 'PUT'))
      OR (resource.url = '/admin/storage/providers/*/validate' AND resource.request_method = 'POST')
WHERE role.role_label = 'admin';
