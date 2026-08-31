CREATE TABLE tb_article_engagement (
    article_id int NOT NULL,
    views_count bigint NOT NULL DEFAULT 0,
    likes_count bigint NOT NULL DEFAULT 0,
    created_at datetime NOT NULL,
    updated_at datetime NOT NULL,
    PRIMARY KEY (article_id),
    CONSTRAINT fk_article_engagement_article
        FOREIGN KEY (article_id) REFERENCES tb_article (id) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章互动事实';

CREATE TABLE tb_article_like (
    user_id int NOT NULL,
    article_id int NOT NULL,
    created_at datetime NOT NULL,
    PRIMARY KEY (user_id, article_id),
    KEY idx_article_like_article (article_id),
    CONSTRAINT fk_article_like_user
        FOREIGN KEY (user_id) REFERENCES tb_user_info (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_like_article
        FOREIGN KEY (article_id) REFERENCES tb_article (id) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章点赞事实';
