-- Add the read-only Xray traffic monitor to the retained administrator surface.
INSERT INTO tb_menu
    (name, code, path, component, route_key, icon, icon_key, section,
     create_time, update_time, order_num, parent_id, is_hidden)
SELECT '代理监控', 'traffic', '/traffic', 'Layout', 'traffic', 'activity', 'activity', 'operations',
       NOW(), NOW(), 40, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM tb_menu WHERE code = 'traffic' OR path = '/traffic'
);

INSERT INTO tb_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM tb_role role
JOIN tb_menu menu ON menu.code = 'traffic'
WHERE role.role_label = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_menu existing
      WHERE existing.role_id = role.id AND existing.menu_id = menu.id
  );

INSERT INTO tb_resource
    (resource_name, url, request_method, parent_id, is_anonymous, create_time, update_time)
SELECT seed.resource_name, seed.url, seed.request_method, parent.id, 0, NOW(), NOW()
FROM (
    SELECT '代理监控概览' AS resource_name, '/admin/traffic/overview' AS url, 'GET' AS request_method
    UNION ALL SELECT '代理连接趋势', '/admin/traffic/timeseries', 'GET'
    UNION ALL SELECT '代理每日趋势', '/admin/traffic/daily', 'GET'
    UNION ALL SELECT '代理来源 IP', '/admin/traffic/sources', 'GET'
    UNION ALL SELECT '代理目标域名', '/admin/traffic/targets', 'GET'
    UNION ALL SELECT '代理来源分布', '/admin/traffic/geo', 'GET'
    UNION ALL SELECT '代理实时连接', '/admin/traffic/live', 'GET'
    UNION ALL SELECT '代理告警', '/admin/traffic/alerts', 'GET'
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
  ON resource.url LIKE '/admin/traffic/%'
 AND resource.request_method = 'GET'
WHERE role.role_label = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_resource existing
      WHERE existing.role_id = role.id AND existing.resource_id = resource.id
  );
