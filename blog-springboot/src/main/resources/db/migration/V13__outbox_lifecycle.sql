ALTER TABLE tb_outbox_event
    ADD COLUMN event_version int NOT NULL DEFAULT 1 AFTER event_type,
    ADD COLUMN trace_id varchar(128) NULL AFTER payload,
    ADD COLUMN enqueued_at datetime NULL AFTER claimed_at,
    ADD COLUMN processing_started_at datetime NULL AFTER enqueued_at,
    ADD COLUMN processed_at datetime NULL AFTER published_at;

UPDATE tb_outbox_event
SET enqueued_at = COALESCE(enqueued_at, created_at),
    event_version = COALESCE(event_version, 1)
WHERE enqueued_at IS NULL OR event_version IS NULL;

DROP INDEX idx_outbox_dispatch ON tb_outbox_event;
CREATE INDEX idx_outbox_dispatch_v2
    ON tb_outbox_event (status, next_attempt_at, claimed_at, created_at);
