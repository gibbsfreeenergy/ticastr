-- Add the first safe administrator controls for the Xray traffic bridge.
INSERT INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
SELECT seed.resource_name, seed.url, seed.request_method, parent.id, 0, NOW(), NOW()
FROM (
    SELECT '查看代理 IP 黑名单' AS resource_name, '/admin/traffic/blocklist' AS url, 'GET' AS request_method
    UNION ALL SELECT '封禁代理来源 IP', '/admin/traffic/block', 'POST'
    UNION ALL SELECT '解除代理来源 IP 封禁', '/admin/traffic/unblock', 'POST'
    UNION ALL SELECT '更新代理来源 IP 备注', '/admin/traffic/label', 'POST'
    UNION ALL SELECT '确认代理告警', '/admin/traffic/alerts/ack', 'POST'
    UNION ALL SELECT '查看代理告警规则', '/admin/traffic/alert-rules', 'GET'
    UNION ALL SELECT '保存代理告警规则', '/admin/traffic/alert-rules', 'POST'
    UNION ALL SELECT '删除代理告警规则', '/admin/traffic/alert-rules/*', 'DELETE'
    UNION ALL SELECT '同步代理 IP 黑名单', '/admin/traffic/blocklist/sync', 'POST'
    UNION ALL SELECT '手动触发代理采集', '/admin/traffic/collect', 'POST'
    UNION ALL SELECT '刷新代理 GeoIP 信息', '/admin/traffic/geo/refresh', 'POST'
) seed
JOIN tb_resource parent
  ON parent.resource_name = 'Admin API'
 AND parent.parent_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM tb_resource existing
    WHERE existing.url = seed.url AND existing.request_method = seed.request_method
);

INSERT INTO tb_role_resource (role_id, resource_id)
SELECT role.id, resource.id
FROM tb_role role
JOIN tb_resource resource
  ON resource.url IN (
      '/admin/traffic/blocklist',
      '/admin/traffic/block',
      '/admin/traffic/unblock',
      '/admin/traffic/label',
      '/admin/traffic/alerts/ack',
      '/admin/traffic/alert-rules',
      '/admin/traffic/alert-rules/*',
      '/admin/traffic/blocklist/sync',
      '/admin/traffic/collect',
      '/admin/traffic/geo/refresh'
  )
WHERE role.role_label = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_resource existing
      WHERE existing.role_id = role.id AND existing.resource_id = resource.id
  );
