-- Separate storage management from personal settings. Keep existing role access.
INSERT INTO tb_menu
    (name, code, path, component, route_key, icon, icon_key, section,
     create_time, update_time, order_num, parent_id, is_hidden)
SELECT '存储源配置', 'storage', '/storage', '/storage/Storage.vue', 'storage',
       'folder', 'folder', 'settings', NOW(), NOW(), 10, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM tb_menu WHERE path = '/storage' OR code = 'storage');

INSERT INTO tb_role_menu (role_id, menu_id)
SELECT DISTINCT role_menu.role_id, storage.id
FROM tb_role_menu role_menu
JOIN tb_menu settings ON settings.id = role_menu.menu_id
JOIN tb_menu storage ON storage.code = 'storage'
WHERE (settings.route_key = 'setting' OR settings.path = '/setting')
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_menu existing
      WHERE existing.role_id = role_menu.role_id AND existing.menu_id = storage.id
  );
