package com.wzh.blog.service;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.ChatRecordDao;
import com.wzh.blog.dto.ChatRecordDTO;
import com.wzh.blog.dto.ChatUserIdentity;
import com.wzh.blog.dto.RecallMessageDTO;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import com.wzh.blog.enums.ChatTypeEnum;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.VoiceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Application use cases for bounded, idempotent chat. */
@Service
public class ChatApplicationService {

    private static final int PROTOCOL_VERSION = 1;
    private static final int DEFAULT_HISTORY_LIMIT = 100;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final ChatRecordDao chatRecordDao;
    private final MediaAssetStore mediaAssetStore;
    private final AssetLifecycleService assetLifecycleService;
    private final BlogInfoService blogInfoService;
    private final ChatBroadcastService chatBroadcastService;
    private final ChatPolicy chatPolicy;
    private final TransactionTemplate transactionTemplate;

    /** Compatibility constructor for focused unit tests and legacy callers. */
    public ChatApplicationService(ChatRecordDao chatRecordDao,
                                  MediaAssetStore mediaAssetStore,
                                  AssetLifecycleService assetLifecycleService,
                                  BlogInfoService blogInfoService,
                                  ChatBroadcastService chatBroadcastService,
                                  ChatPolicy chatPolicy) {
        this(chatRecordDao, mediaAssetStore, assetLifecycleService, blogInfoService,
                chatBroadcastService, chatPolicy, null);
    }

    @Autowired
    public ChatApplicationService(ChatRecordDao chatRecordDao,
                                  MediaAssetStore mediaAssetStore,
                                  AssetLifecycleService assetLifecycleService,
                                  BlogInfoService blogInfoService,
                                  ChatBroadcastService chatBroadcastService,
                                  ChatPolicy chatPolicy,
                                  PlatformTransactionManager transactionManager) {
        this.chatRecordDao = chatRecordDao;
        this.mediaAssetStore = mediaAssetStore;
        this.assetLifecycleService = assetLifecycleService;
        this.blogInfoService = blogInfoService;
        this.chatBroadcastService = chatBroadcastService;
        this.chatPolicy = chatPolicy;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    public ChatRecordDTO history(ChatUserIdentity identity) {
        return history(identity, null, DEFAULT_HISTORY_LIMIT);
    }

    public ChatRecordDTO history(ChatUserIdentity identity, Integer beforeId, Integer requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_HISTORY_LIMIT,
                requestedLimit == null ? DEFAULT_HISTORY_LIMIT : requestedLimit));
        LambdaQueryWrapper<ChatRecord> query = new LambdaQueryWrapper<ChatRecord>()
                .ge(ChatRecord::getCreateTime, DateUtil.offsetHour(new Date(), -12))
                .orderByDesc(ChatRecord::getId)
                .last("LIMIT " + (limit + 1));
        if (beforeId != null && beforeId > 0) {
            query.lt(ChatRecord::getId, beforeId);
        }
        List<ChatRecord> records = chatRecordDao.selectList(query);
        boolean hasMore = records.size() > limit;
        if (hasMore) records = records.subList(0, limit);
        Collections.reverse(records);
        records.forEach(record -> record.setOwner(
                chatPolicy.owns(record.getClientToken(), identity.clientToken())));
        return ChatRecordDTO.builder()
                .chatRecordList(records)
                .ipAddress(identity.ipAddress())
                .ipSource(identity.ipSource())
                .nextBeforeId(hasMore && !records.isEmpty() ? records.get(0).getId() : null)
                .hasMore(hasMore)
                .build();
    }

    public String guestAvatar() {
        return blogInfoService.getWebsiteConfig().getTouristAvatar();
    }

    public void registerSession(String sessionId) {
        chatBroadcastService.registerSession(sessionId);
    }

    public void unregisterSession(String sessionId) {
        chatBroadcastService.unregisterSession(sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Optional<WebsocketMessageDTO> handle(WebsocketMessageDTO incoming,
                                                ChatUserIdentity identity,
                                                String sessionId) {
        if (incoming == null || identity == null || ChatTypeEnum.getChatType(incoming.getType()) == null) {
            return Optional.of(errorMessage("INVALID_MESSAGE", "消息格式或内容不合法"));
        }
        if (incoming.getVersion() != null && incoming.getVersion() != PROTOCOL_VERSION) {
            return Optional.of(errorMessage("UNSUPPORTED_VERSION", "聊天协议版本不受支持"));
        }
        ChatTypeEnum type = ChatTypeEnum.getChatType(incoming.getType());
        switch (type) {
            case SEND_MESSAGE -> {
                String clientMessageId = chatPolicy.clientMessageId(incoming.getClientMessageId());
                ChatRecord existing = chatRecordDao.selectByClientMessage(identity.clientToken(), clientMessageId);
                if (existing != null) {
                    return Optional.of(acknowledgement(existing, clientMessageId));
                }
                ChatRecord record = chatPolicy.textRecord(incoming.getData(), identity);
                record.setClientMessageId(clientMessageId);
                record.setType(ChatTypeEnum.SEND_MESSAGE.getType());
                if (chatRecordDao.insertIgnore(record) == 0) {
                    existing = chatRecordDao.selectByClientMessage(identity.clientToken(), clientMessageId);
                    if (existing == null) throw new BizException("消息幂等记录不可用");
                    return Optional.of(acknowledgement(existing, clientMessageId));
                }
                WebsocketMessageDTO event = protocolMessage(type, record);
                chatBroadcastService.publishAfterCommit(event);
                return Optional.of(acknowledgement(record, clientMessageId));
            }
            case RECALL_MESSAGE -> {
                RecallMessageDTO recall = chatPolicy.recall(incoming.getData(), identity.clientToken());
                if (recall == null) return Optional.empty();
                ChatRecord record = chatRecordDao.selectById(recall.getId());
                if (record == null || !chatPolicy.owns(record.getClientToken(), identity.clientToken())) {
                    return Optional.empty();
                }
                chatRecordDao.deleteById(recall.getId());
                chatBroadcastService.publishAfterCommit(protocolMessage(type, recall));
            }
            case HISTORY_REQUEST -> {
                Map<?, ?> request = JSON.parseObject(JSON.toJSONString(incoming.getData()), Map.class);
                Integer beforeId = integerValue(request == null ? null : request.get("beforeId"));
                Integer limit = integerValue(request == null ? null : request.get("limit"));
                return Optional.of(protocolMessage(typeForHistory(),
                        history(identity, beforeId, limit)));
            }
            case HEART_BEAT -> {
                chatBroadcastService.touchSession(sessionId);
                return Optional.of(protocolMessage(ChatTypeEnum.HEART_BEAT, "pong"));
            }
            default -> {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public void sendVoice(VoiceVO voiceVO) {
        String clientToken = chatPolicy.requireClientToken(voiceVO.getClientId());
        String clientMessageId = chatPolicy.clientMessageId(voiceVO.getClientMessageId());
        String content = mediaAssetStore.upload(voiceVO.getFile(), FilePathEnum.VOICE.getPath());
        voiceVO.setContent(content);
        try {
            Runnable persist = () -> persistVoice(voiceVO, clientToken, clientMessageId, content);
            if (transactionTemplate == null) {
                // The compatibility constructor is only used by focused unit
                // tests; production always supplies the database transaction.
                persist.run();
            } else {
                transactionTemplate.executeWithoutResult(status -> persist.run());
            }
        } catch (RuntimeException exception) {
            // Uploading is deliberately outside the DB transaction. If the
            // short persistence transaction fails, clean the object through the
            // same durable, reference-aware lifecycle as every other asset.
            assetLifecycleService.deleteAfterCommit(List.of(content));
            throw exception;
        }
    }

    private void persistVoice(VoiceVO voiceVO, String clientToken,
                              String clientMessageId, String content) {
        ChatRecord existing = chatRecordDao.selectByClientMessage(clientToken, clientMessageId);
        if (existing != null) {
            assetLifecycleService.deleteAfterCommit(List.of(content));
            return;
        }
        ChatRecord chatRecord = BeanCopyUtils.copyObject(voiceVO, ChatRecord.class);
        chatRecord.setId(null);
        chatRecord.setType(ChatTypeEnum.VOICE_MESSAGE.getType());
        chatRecord.setClientToken(clientToken);
        chatRecord.setClientMessageId(clientMessageId);
        if (chatRecordDao.insertIgnore(chatRecord) == 0) {
            assetLifecycleService.deleteAfterCommit(List.of(content));
            return;
        }
        chatBroadcastService.publishAfterCommit(protocolMessage(ChatTypeEnum.VOICE_MESSAGE, chatRecord));
    }

    private WebsocketMessageDTO protocolMessage(ChatTypeEnum type, Object data) {
        WebsocketMessageDTO.WebsocketMessageDTOBuilder builder = WebsocketMessageDTO.builder()
                .type(type.getType())
                .eventId(UUID.randomUUID().toString())
                .version(PROTOCOL_VERSION)
                .serverTime(Instant.now())
                .data(data);
        if (data instanceof ChatRecord record) {
            builder.messageId(record.getId()).clientMessageId(record.getClientMessageId());
        }
        return builder.build();
    }

    private WebsocketMessageDTO acknowledgement(ChatRecord record, String clientMessageId) {
        return WebsocketMessageDTO.builder()
                .type(ChatTypeEnum.MESSAGE_ACK.getType())
                .eventId(UUID.randomUUID().toString())
                .version(PROTOCOL_VERSION)
                .serverTime(Instant.now())
                .clientMessageId(clientMessageId)
                .messageId(record.getId())
                .data(Map.of("clientMessageId", clientMessageId, "messageId", record.getId()))
                .build();
    }

    private WebsocketMessageDTO errorMessage(String code, String message) {
        return WebsocketMessageDTO.builder()
                .type(ChatTypeEnum.CHAT_ERROR.getType())
                .eventId(UUID.randomUUID().toString())
                .version(PROTOCOL_VERSION)
                .serverTime(Instant.now())
                .data(Map.of("code", code, "message", message))
                .build();
    }

    private ChatTypeEnum typeForHistory() {
        return ChatTypeEnum.HISTORY_RECORD;
    }

    private Integer integerValue(Object value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
