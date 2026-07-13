UPDATE `tb_website_config`
SET `config` = '{}'
WHERE `config` IS NOT NULL AND NOT JSON_VALID(`config`);

UPDATE `tb_talk`
SET `images` = NULL
WHERE `images` IS NOT NULL AND NOT JSON_VALID(`images`);

ALTER TABLE `tb_website_config`
    MODIFY `config` json NULL COMMENT 'typed website configuration';

ALTER TABLE `tb_talk`
    MODIFY `images` json NULL COMMENT 'talk image URL array';
