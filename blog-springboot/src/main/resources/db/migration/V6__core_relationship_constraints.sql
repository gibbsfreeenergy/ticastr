-- Repair legacy orphan rows before enforcing relationships used by core writes.
DELETE auth
FROM `tb_user_auth` auth
LEFT JOIN `tb_user_info` user_info ON user_info.id = auth.user_info_id
WHERE user_info.id IS NULL;

UPDATE `tb_article` article
LEFT JOIN `tb_category` category ON category.id = article.category_id
SET article.category_id = NULL
WHERE article.category_id IS NOT NULL AND category.id IS NULL;

DELETE photo
FROM `tb_photo` photo
LEFT JOIN `tb_photo_album` album ON album.id = photo.album_id
WHERE album.id IS NULL;

UPDATE `tb_comment` reply
LEFT JOIN `tb_comment` parent ON parent.id = reply.parent_id
SET reply.parent_id = NULL
WHERE reply.parent_id IS NOT NULL AND parent.id IS NULL;

ALTER TABLE `tb_user_auth`
    ADD CONSTRAINT `fk_user_auth_user_info`
        FOREIGN KEY (`user_info_id`) REFERENCES `tb_user_info` (`id`) ON DELETE CASCADE;
ALTER TABLE `tb_article`
    ADD CONSTRAINT `fk_article_category`
        FOREIGN KEY (`category_id`) REFERENCES `tb_category` (`id`) ON DELETE SET NULL;
ALTER TABLE `tb_photo`
    ADD CONSTRAINT `fk_photo_album`
        FOREIGN KEY (`album_id`) REFERENCES `tb_photo_album` (`id`) ON DELETE CASCADE;
ALTER TABLE `tb_comment`
    ADD CONSTRAINT `fk_comment_parent`
        FOREIGN KEY (`parent_id`) REFERENCES `tb_comment` (`id`) ON DELETE CASCADE;
