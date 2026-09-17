-- Keep the storage API permissions aligned with the standalone storage menu.
INSERT IGNORE INTO tb_role_resource (role_id, resource_id)
SELECT DISTINCT role_menu.role_id, resource.id
FROM tb_role_menu role_menu
JOIN tb_menu storage_menu ON storage_menu.id = role_menu.menu_id
JOIN tb_resource resource
  ON resource.parent_id IS NOT NULL
 AND resource.is_anonymous = 0
 AND resource.request_method IS NOT NULL
 AND (resource.url = '/admin/storage/configs'
      OR resource.url LIKE '/admin/storage/configs/%')
WHERE storage_menu.code = 'storage'
  AND storage_menu.path = '/storage';
