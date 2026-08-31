CREATE TABLE `tb_outbox_event` (
    `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '事件id',
    `event_type` varchar(100) NOT NULL COMMENT '事件类型',
    `aggregate_id` varchar(128) NULL COMMENT '聚合id',
    `payload` longtext NOT NULL COMMENT '版本化事件内容',
    `status` varchar(20) NOT NULL COMMENT 'PENDING PROCESSING PUBLISHED DEAD',
    `attempts` int NOT NULL DEFAULT 0 COMMENT '投递尝试次数',
    `next_attempt_at` datetime NOT NULL COMMENT '下一次投递时间',
    `claimed_at` datetime NULL COMMENT '领取时间',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `published_at` datetime NULL COMMENT '成功投递时间',
    `last_error` varchar(1000) NULL COMMENT '最近错误',
    PRIMARY KEY (`event_id`),
    INDEX `idx_outbox_dispatch` (`status`, `next_attempt_at`, `claimed_at`, `created_at`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '事务消息发件箱';
