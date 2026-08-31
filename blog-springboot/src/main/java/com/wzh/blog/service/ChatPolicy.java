package com.wzh.blog.service;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.ChatUserIdentity;
import com.wzh.blog.dto.RecallMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.util.ChatIdentityUtils;
import com.wzh.blog.util.HTMLUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/** Pure chat input and ownership policy kept outside the WebSocket adapter. */
@Service
public class ChatPolicy {

    public ChatRecord textRecord(Object data, ChatUserIdentity identity) {
        ChatRecord record = JSON.parseObject(JSON.toJSONString(data), ChatRecord.class);
        if (record == null || record.getContent() == null || record.getContent().isBlank()
                || record.getContent().length() > 1000) {
            throw new BizException("聊天内容不能为空或超过长度限制");
        }
        record.setContent(HTMLUtils.filter(record.getContent()));
        record.setId(null);
        record.setUserId(identity.userId());
        record.setNickname(identity.userId() == null ? "游客" : identity.nickname());
        record.setAvatar(identity.userId() == null ? identity.avatar() : identity.avatar());
        record.setIpAddress(identity.ipAddress());
        record.setIpSource(identity.ipSource());
        record.setClientToken(identity.clientToken());
        return record;
    }

    public RecallMessageDTO recall(Object data, String clientToken) {
        RecallMessageDTO recall = JSON.parseObject(JSON.toJSONString(data), RecallMessageDTO.class);
        if (recall == null || recall.getId() == null || clientToken == null) {
            return null;
        }
        return recall;
    }

    public String clientMessageId(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        if (value.length() > 64 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new BizException("消息幂等标识不合法");
        }
        return value;
    }

    public String requireClientToken(String clientId) {
        if (!ChatIdentityUtils.isValidClientId(clientId)) {
            throw new BizException("Invalid client identity");
        }
        return ChatIdentityUtils.hashClientId(clientId);
    }

    public boolean owns(String recordToken, String clientToken) {
        return recordToken != null && Objects.equals(recordToken, clientToken);
    }
}
