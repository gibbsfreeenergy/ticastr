package com.wzh.blog.service;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import org.junit.jupiter.api.Test;

import static com.wzh.blog.enums.ChatTypeEnum.SEND_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

class ChatBroadcastServiceTest {

    @Test
    void restoresChatRecordAfterRedisJsonDeserialization() {
        ChatRecord source = ChatRecord.builder()
                .id(1)
                .content("hello")
                .nickname("visitor")
                .type(SEND_MESSAGE.getType())
                .build();
        WebsocketMessageDTO message = JSON.parseObject(JSON.toJSONString(WebsocketMessageDTO.builder()
                .type(SEND_MESSAGE.getType())
                .data(source)
                .build()), WebsocketMessageDTO.class);

        ChatBroadcastService.restoreChatRecord(message);

        assertThat(message.getData()).isInstanceOf(ChatRecord.class);
        assertThat(((ChatRecord) message.getData()).getContent()).isEqualTo("hello");
    }
}
