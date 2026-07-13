package com.wzh.blog.service.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.ChatRecordDao;
import com.wzh.blog.dto.UserDetailDTO;
import com.wzh.blog.service.BlogInfoService;
import com.wzh.blog.service.ChatBroadcastService;
import com.wzh.blog.dto.ChatRecordDTO;
import com.wzh.blog.dto.RecallMessageDTO;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.strategy.context.UploadStrategyContext;
import com.wzh.blog.util.*;
import com.wzh.blog.vo.VoiceVO;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.wzh.blog.enums.ChatTypeEnum.*;

/**
 * websocket服务
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Data
@Service
@Log4j2
@ServerEndpoint(value = "/websocket", configurator = WebSocketServiceImpl.ChatConfigurator.class)
public class WebSocketServiceImpl {

    /**
     * 用户session
     */
    private Session session;

    private String clientIpAddress;

    private String clientIpSource;

    private String clientToken;

    private Integer userId;

    private String nickname;

    private String avatar;

    /**
     * 用户session集合
     */
    private static final CopyOnWriteArraySet<WebSocketServiceImpl> webSocketSet = new CopyOnWriteArraySet<>();

    @Autowired
    public void setChatRecordDao(ChatRecordDao chatRecordDao) {
        WebSocketServiceImpl.chatRecordDao = chatRecordDao;
    }

    @Autowired
    public void setUploadStrategyContext(UploadStrategyContext uploadStrategyContext) {
        WebSocketServiceImpl.uploadStrategyContext = uploadStrategyContext;
    }

    private static ChatRecordDao chatRecordDao;

    private static UploadStrategyContext uploadStrategyContext;

    private static BlogInfoService blogInfoService;

    private static ChatBroadcastService chatBroadcastService;

    @Autowired
    public void setBlogInfoService(BlogInfoService blogInfoService) {
        WebSocketServiceImpl.blogInfoService = blogInfoService;
    }

    @Autowired
    public void setChatBroadcastService(ChatBroadcastService chatBroadcastService) {
        WebSocketServiceImpl.chatBroadcastService = chatBroadcastService;
    }

    /**
     * 获取客户端真实ip
     */
    public static class ChatConfigurator extends ServerEndpointConfig.Configurator {

        public static String HEADER_NAME = "X-Real-IP";
        public static String CLIENT_ID_PROPERTY = "clientId";
        public static String USER_DETAIL_PROPERTY = "userDetail";

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            String clientId = getClientId(request.getQueryString());
            if (clientId != null) {
                sec.getUserProperties().put(CLIENT_ID_PROPERTY, clientId);
            }
            getLoginUser(request.getHttpSession()).ifPresent(user -> sec.getUserProperties().put(USER_DETAIL_PROPERTY, user));
            try {
                String firstFoundHeader = request.getHeaders().get(HEADER_NAME.toLowerCase()).get(0);
                sec.getUserProperties().put(HEADER_NAME, firstFoundHeader);
            } catch (Exception e) {
                sec.getUserProperties().put(HEADER_NAME, "未知ip");
            }
        }

        private String getClientId(String queryString) {
            if (queryString == null || queryString.isBlank()) {
                return null;
            }
            for (String parameter : queryString.split("&")) {
                String[] pair = parameter.split("=", 2);
                if (pair.length == 2 && "clientId".equals(pair[0])) {
                    try {
                        String clientId = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        return ChatIdentityUtils.isValidClientId(clientId) ? clientId : null;
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }

        private java.util.Optional<UserDetailDTO> getLoginUser(Object httpSession) {
            if (!(httpSession instanceof HttpSession session)) {
                return java.util.Optional.empty();
            }
            Object securityContext = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (!(securityContext instanceof SecurityContext context)) {
                return java.util.Optional.empty();
            }
            Authentication authentication = context.getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof UserDetailDTO userDetail) {
                return java.util.Optional.of(userDetail);
            }
            return java.util.Optional.empty();
        }
    }

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig) throws IOException {
        // 加入连接
        this.session = session;
        this.clientIpAddress = Objects.toString(endpointConfig.getUserProperties().get(ChatConfigurator.HEADER_NAME), "");
        String clientId = (String) endpointConfig.getUserProperties().get(ChatConfigurator.CLIENT_ID_PROPERTY);
        if (clientId == null) {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Missing client identity"));
            return;
        }
        this.clientToken = ChatIdentityUtils.hashClientId(clientId);
        UserDetailDTO userDetail = (UserDetailDTO) endpointConfig.getUserProperties().get(ChatConfigurator.USER_DETAIL_PROPERTY);
        if (userDetail != null) {
            this.userId = userDetail.getUserInfoId();
            this.nickname = userDetail.getNickname();
            this.avatar = userDetail.getAvatar();
        }
        this.clientIpSource = IpUtils.getIpSource(clientIpAddress);
        webSocketSet.add(this);
        // 更新在线人数
        chatBroadcastService.registerSession(session.getId());
        // 加载历史聊天记录
        ChatRecordDTO chatRecordDTO = listChartRecords();
        // 发送消息
        WebsocketMessageDTO messageDTO = WebsocketMessageDTO.builder()
                .type(HISTORY_RECORD.getType())
                .data(chatRecordDTO)
                .build();
        synchronized (session) {
            session.getBasicRemote().sendText(JSON.toJSONString(messageDTO));
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        WebsocketMessageDTO messageDTO = JSON.parseObject(message, WebsocketMessageDTO.class);
        if (messageDTO == null || getChatType(messageDTO.getType()) == null) {
            return;
        }
        switch (getChatType(messageDTO.getType())) {
            case SEND_MESSAGE:
                // 发送消息
                ChatRecord chatRecord = JSON.parseObject(JSON.toJSONString(messageDTO.getData()), ChatRecord.class);
                if (chatRecord == null || chatRecord.getContent() == null || chatRecord.getContent().isBlank()
                        || chatRecord.getContent().length() > 1000) {
                    return;
                }
                // 过滤html标签
                chatRecord.setContent(HTMLUtils.filter(chatRecord.getContent()));
                chatRecord.setId(null);
                applySender(chatRecord);
                chatRecord.setType(SEND_MESSAGE.getType());
                chatRecord.setIpAddress(clientIpAddress);
                chatRecord.setIpSource(clientIpSource);
                chatRecord.setClientToken(clientToken);
                chatRecordDao.insert(chatRecord);
                messageDTO.setData(chatRecord);
                // 广播消息
                chatBroadcastService.publish(messageDTO);
                break;
            case RECALL_MESSAGE:
                // 撤回消息
                RecallMessageDTO recallMessage = JSON.parseObject(JSON.toJSONString(messageDTO.getData()), RecallMessageDTO.class);
                if (recallMessage == null || recallMessage.getId() == null) {
                    return;
                }
                ChatRecord recalledRecord = chatRecordDao.selectById(recallMessage.getId());
                if (recalledRecord == null || recalledRecord.getClientToken() == null
                        || !Objects.equals(recalledRecord.getClientToken(), clientToken)) {
                    return;
                }
                // 删除记录
                chatRecordDao.deleteById(recallMessage.getId());
                // 广播消息
                chatBroadcastService.publish(messageDTO);
                break;
            case HEART_BEAT:
                // 心跳消息
                chatBroadcastService.touchSession(session.getId());
                messageDTO.setData("pong");
                session.getBasicRemote().sendText(JSON.toJSONString(messageDTO));
                break;
            default:
                break;
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() throws IOException {
        // 更新在线人数
        webSocketSet.remove(this);
        chatBroadcastService.unregisterSession(session.getId());
    }

    /**
     * 加载历史聊天记录
     *
     * @param endpointConfig 配置
     * @return 加载历史聊天记录
     */
    private ChatRecordDTO listChartRecords() {
        // 获取聊天历史记录
        List<ChatRecord> chatRecordList = chatRecordDao.selectList(new LambdaQueryWrapper<ChatRecord>()
                .ge(ChatRecord::getCreateTime, DateUtil.offsetHour(new Date(), -12)));
        chatRecordList.forEach(record -> record.setOwner(Objects.equals(record.getClientToken(), clientToken)));
        // 获取当前用户ip
        return ChatRecordDTO.builder()
                .chatRecordList(chatRecordList)
                .ipAddress(clientIpAddress)
                .ipSource(clientIpSource)
                .build();
    }

    /**
     * 发送语音
     *
     * @param voiceVO 语音路径
     */
    public void sendVoice(VoiceVO voiceVO) {
        if (!ChatIdentityUtils.isValidClientId(voiceVO.getClientId())) {
            throw new BizException("Invalid client identity");
        }
        // 上传语音文件
        String content = uploadStrategyContext.executeUploadStrategy(voiceVO.getFile(), FilePathEnum.VOICE.getPath());
        voiceVO.setContent(content);
        // 保存记录
        ChatRecord chatRecord = BeanCopyUtils.copyObject(voiceVO, ChatRecord.class);
        chatRecord.setId(null);
        chatRecord.setType(VOICE_MESSAGE.getType());
        chatRecord.setClientToken(ChatIdentityUtils.hashClientId(voiceVO.getClientId()));
        chatRecordDao.insert(chatRecord);
        // 发送消息
        WebsocketMessageDTO messageDTO = WebsocketMessageDTO.builder()
                .type(VOICE_MESSAGE.getType())
                .data(chatRecord)
                .build();
        // 广播消息
        chatBroadcastService.publish(messageDTO);
    }

    private void applySender(ChatRecord chatRecord) {
        chatRecord.setUserId(userId);
        if (userId != null) {
            chatRecord.setNickname(nickname);
            chatRecord.setAvatar(avatar);
            return;
        }
        chatRecord.setNickname("游客");
        chatRecord.setAvatar(blogInfoService.getWebsiteConfig().getTouristAvatar());
    }

    /**
     * 广播消息
     *
     * @param messageDTO 消息dto
     * @throws IOException io异常
     */
    public static void broadcastLocal(WebsocketMessageDTO messageDTO) {
        for (WebSocketServiceImpl webSocketService : webSocketSet) {
            if (!webSocketService.session.isOpen()) {
                webSocketSet.remove(webSocketService);
                continue;
            }
            try {
                synchronized (webSocketService.session) {
                WebsocketMessageDTO outboundMessage = messageDTO;
                if (messageDTO.getData() instanceof ChatRecord chatRecord) {
                    ChatRecord visibleRecord = BeanCopyUtils.copyObject(chatRecord, ChatRecord.class);
                    visibleRecord.setOwner(Objects.equals(chatRecord.getClientToken(), webSocketService.clientToken));
                    outboundMessage = WebsocketMessageDTO.builder()
                            .type(messageDTO.getType())
                            .data(visibleRecord)
                            .build();
                }
                webSocketService.session.getBasicRemote().sendText(JSON.toJSONString(outboundMessage));
                }
            } catch (IOException e) {
                log.warn("Unable to deliver chat event to local WebSocket session", e);
            }
        }
    }

}
