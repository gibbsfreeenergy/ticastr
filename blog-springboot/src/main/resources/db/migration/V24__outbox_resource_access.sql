-- The personal-center outbox panel is an administrator-only operational surface.
INSERT IGNORE INTO tb_role_resource (role_id, resource_id)
SELECT role.id, resource.id
FROM tb_role role
JOIN tb_resource resource
  ON resource.request_method IS NOT NULL
 AND (resource.url = '/admin/outbox'
      OR resource.url = '/admin/outbox/metrics'
      OR resource.url = '/admin/outbox/*/retry')
WHERE role.role_label = 'admin';
