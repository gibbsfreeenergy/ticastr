ALTER TABLE tb_article
    ADD INDEX idx_article_public_cursor (is_delete, status, create_time, id);

ALTER TABLE tb_article_tag
    ADD INDEX idx_article_tag_tag_article (tag_id, article_id);

ALTER TABLE tb_article_engagement
    ADD INDEX idx_article_engagement_updated (updated_at, article_id);

ALTER TABLE tb_outbox_event
    ADD INDEX idx_outbox_status_created (status, created_at, event_id);
