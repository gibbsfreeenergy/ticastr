package com.wzh.blog.service;

import com.wzh.blog.dao.ChatRecordDao;
import com.wzh.blog.dto.ChatUserIdentity;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import com.wzh.blog.enums.ChatTypeEnum;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.vo.VoiceVO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatProtocolContractTest {

    @Test
    void newMessageGetsAnEnvelopeAndAnIdempotentAck() {
        ChatRecordDao dao = mock(ChatRecordDao.class);
        doAnswer(invocation -> {
            invocation.<ChatRecord>getArgument(0).setId(42);
            return 1;
        }).when(dao).insertIgnore(any(ChatRecord.class));
        ChatBroadcastService broadcast = mock(ChatBroadcastService.class);
        ChatApplicationService service = new ChatApplicationService(
                dao,
                mock(MediaAssetStore.class),
                mock(AssetLifecycleService.class),
                mock(BlogInfoService.class),
                broadcast,
                new ChatPolicy());

        var ack = service.handle(
                WebsocketMessageDTO.builder()
                        .type(ChatTypeEnum.SEND_MESSAGE.getType())
                        .clientMessageId("client-42")
                        .data(Map.of("content", "hello", "type", 3))
                        .build(),
                new ChatUserIdentity(null, "游客", "/avatar", "127.0.0.1", "local", "token"),
                "session-1");

        assertThat(ack).isPresent();
        assertThat(ack.orElseThrow().getType()).isEqualTo(ChatTypeEnum.MESSAGE_ACK.getType());
        assertThat(ack.orElseThrow().getEventId()).isNotBlank();
        assertThat(ack.orElseThrow().getServerTime()).isNotNull();
        assertThat(ack.orElseThrow().getMessageId()).isEqualTo(42);
        verify(broadcast).publishAfterCommit(argThat(event ->
                event.getType().equals(ChatTypeEnum.SEND_MESSAGE.getType())
                        && event.getMessageId().equals(42)
                        && event.getServerTime() != null
                        && event.getEventId() != null));
    }

    @Test
    void retryReturnsTheOriginalAckWithoutBroadcastingAnotherMessage() {
        ChatRecord existing = ChatRecord.builder()
                .id(7)
                .clientMessageId("client-7")
                .clientToken("token")
                .build();
        ChatRecordDao dao = mock(ChatRecordDao.class);
        when(dao.selectByClientMessage("token", "client-7")).thenReturn(existing);
        ChatBroadcastService broadcast = mock(ChatBroadcastService.class);
        ChatApplicationService service = new ChatApplicationService(
                dao,
                mock(MediaAssetStore.class),
                mock(AssetLifecycleService.class),
                mock(BlogInfoService.class),
                broadcast,
                new ChatPolicy());

        var ack = service.handle(
                WebsocketMessageDTO.builder()
                        .type(ChatTypeEnum.SEND_MESSAGE.getType())
                        .clientMessageId("client-7")
                        .data(Map.of("content", "retry"))
                        .build(),
                new ChatUserIdentity(null, "游客", "/avatar", "127.0.0.1", "local", "token"),
                "session-1");

        assertThat(ack).isPresent();
        assertThat(ack.orElseThrow().getMessageId()).isEqualTo(7);
        verify(dao, never()).insertIgnore(any(ChatRecord.class));
        verify(broadcast, never()).publishAfterCommit(any(WebsocketMessageDTO.class));
    }

    @Test
    void failedVoicePersistenceSchedulesCompensationForTheUploadedObject() {
        ChatRecordDao dao = mock(ChatRecordDao.class);
        when(dao.insertIgnore(any(ChatRecord.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        MediaAssetStore mediaStore = mock(MediaAssetStore.class);
        when(mediaStore.upload(any(), anyString())).thenReturn("/uploads/voice-1.wav");
        AssetLifecycleService lifecycle = mock(AssetLifecycleService.class);
        ChatApplicationService service = new ChatApplicationService(
                dao, mediaStore, lifecycle, mock(BlogInfoService.class),
                mock(ChatBroadcastService.class), new ChatPolicy());

        VoiceVO voice = VoiceVO.builder()
                .clientId("00000000-0000-0000-0000-000000000042")
                .clientMessageId("voice-1")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendVoice(voice))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(lifecycle).deleteAfterCommit(java.util.List.of("/uploads/voice-1.wav"));
    }
}
