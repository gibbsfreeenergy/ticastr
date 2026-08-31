-- A client message id is stable across retries. The token is server-derived
-- from the browser identity, so two clients cannot collide on the same id.
ALTER TABLE tb_chat_record
    ADD COLUMN client_message_id varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NULL
        COMMENT '客户端消息幂等键' AFTER client_token,
    ADD UNIQUE KEY uk_chat_client_message (client_token, client_message_id);
