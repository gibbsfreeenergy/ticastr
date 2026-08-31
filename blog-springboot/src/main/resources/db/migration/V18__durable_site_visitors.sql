CREATE TABLE tb_site_visitor (
    view_date date NOT NULL,
    visitor_hash char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    area varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    created_at datetime NOT NULL,
    PRIMARY KEY (view_date, visitor_hash),
    KEY idx_site_visitor_date (view_date)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '去重访客事实';
