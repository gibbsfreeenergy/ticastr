package com.wzh.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聊天类型枚举
 *
 * @author yezhiqiu
 * @date 2021/08/06
 */
@Getter
@AllArgsConstructor
public enum ChatTypeEnum {
    /** Invalid frame or application-level error. */
    CHAT_ERROR(0, "聊天错误"),
    /**
     * 在线人数
     */
    ONLINE_COUNT(1, "在线人数"),
    /**
     * 历史记录
     */
    HISTORY_RECORD(2, "历史记录"),
    /**
     * 发送消息
     */
    SEND_MESSAGE(3, "发送消息"),
    /**
     * 撤回消息
     */
    RECALL_MESSAGE(4, "撤回消息"),
    /**
     * 语音消息
     */
    VOICE_MESSAGE(5,"语音消息"),
    /**
     * 心跳消息
     */
    HEART_BEAT(6,"心跳消息"),
    /** Client-visible acknowledgement for an idempotent send. */
    MESSAGE_ACK(7, "消息确认"),
    /** Bounded history page request. */
    HISTORY_REQUEST(8, "历史请求");

    /**
     * 类型
     */
    private final Integer type;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 根据类型获取枚举
     * @param type 类型
     * @return 枚举
     */
    public static ChatTypeEnum getChatType(Integer type) {
        for (ChatTypeEnum chatType : ChatTypeEnum.values()) {
            if (chatType.getType().equals(type)) {
                return chatType;
            }
        }
        return null;
    }

}
