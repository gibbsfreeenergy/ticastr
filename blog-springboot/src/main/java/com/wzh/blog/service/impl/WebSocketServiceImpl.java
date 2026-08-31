package com.wzh.blog.service.impl;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.ChatUserIdentity;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.service.ChatApplicationService;
import com.wzh.blog.service.ChatConnectionRegistry;
import com.wzh.blog.util.ChatIdentityUtils;
import com.wzh.blog.util.IpUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin JSR-356 adapter. Connection lifecycle and frame conversion stay here;
 * chat rules, persistence, upload and broadcast are delegated to application
 * services and ports.
 */
@Log4j2
@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ServerEndpoint(value = "/websocket", configurator = WebSocketServiceImpl.ChatConfigurator.class)
public class WebSocketServiceImpl {

    private final ChatApplicationService chatApplicationService;
    private final ChatConnectionRegistry connectionRegistry;

    private Session session;
    private String clientIpAddress;
    private String clientIpSource;
    private String clientToken;
    private Integer userId;
    private String nickname;
    private String avatar;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean registered;

    public WebSocketServiceImpl(ChatApplicationService chatApplicationService,
                                ChatConnectionRegistry connectionRegistry) {
        this.chatApplicationService = chatApplicationService;
        this.connectionRegistry = connectionRegistry;
    }

    /** Gets the client IP and safe identity during the HTTP handshake. */
    public static class ChatConfigurator extends SpringConfigurator {

        public static final String HEADER_NAME = "X-Real-IP";
        public static final String CLIENT_ID_PROPERTY = "clientId";
        public static final String USER_DETAIL_PROPERTY = "userDetail";

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request,
                                    HandshakeResponse response) {
            String clientId = getClientId(request.getQueryString());
            if (clientId != null) {
                sec.getUserProperties().put(CLIENT_ID_PROPERTY, clientId);
            }
            getLoginUser(request.getHttpSession()).ifPresent(
                    user -> sec.getUserProperties().put(USER_DETAIL_PROPERTY, user));
            try {
                String firstFoundHeader = request.getHeaders().get(HEADER_NAME.toLowerCase()).get(0);
                sec.getUserProperties().put(HEADER_NAME, firstFoundHeader);
            } catch (Exception exception) {
                sec.getUserProperties().put(HEADER_NAME, "未知ip");
            }
        }

        private String getClientId(String queryString) {
            if (queryString == null || queryString.isBlank()) {
                return null;
            }
            for (String parameter : queryString.split("&")) {
                String[] pair = parameter.split("=", 2);
                if (pair.length == 2 && CLIENT_ID_PROPERTY.equals(pair[0])) {
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

        private Optional<AuthenticatedUserPrincipal> getLoginUser(Object httpSession) {
            if (!(httpSession instanceof HttpSession session)) {
                return Optional.empty();
            }
            Object securityContext = session.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (!(securityContext instanceof SecurityContext context)) {
                return Optional.empty();
            }
            Authentication authentication = context.getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal userDetail) {
                return Optional.of(userDetail);
            }
            return Optional.empty();
        }
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig) throws IOException {
        this.session = session;
        this.closed.set(false);
        this.registered = false;
        this.clientIpAddress = String.valueOf(
                endpointConfig.getUserProperties().getOrDefault(ChatConfigurator.HEADER_NAME, ""));
        String clientId = (String) endpointConfig.getUserProperties().get(ChatConfigurator.CLIENT_ID_PROPERTY);
        if (clientId == null) {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Missing client identity"));
            return;
        }
        this.clientToken = ChatIdentityUtils.hashClientId(clientId);
        AuthenticatedUserPrincipal userDetail = (AuthenticatedUserPrincipal) endpointConfig.getUserProperties()
                .get(ChatConfigurator.USER_DETAIL_PROPERTY);
        if (userDetail != null) {
            this.userId = userDetail.getUserInfoId();
            this.nickname = userDetail.getNickname();
            this.avatar = userDetail.getAvatar();
        } else {
            this.avatar = chatApplicationService.guestAvatar();
        }
        this.clientIpSource = IpUtils.getIpSource(clientIpAddress);
        connectionRegistry.register(session, clientToken);
        chatApplicationService.registerSession(session.getId());
        this.registered = true;
        WebsocketMessageDTO history = WebsocketMessageDTO.builder()
                .type(com.wzh.blog.enums.ChatTypeEnum.HISTORY_RECORD.getType())
                .version(1)
                .eventId(UUID.randomUUID().toString())
                .serverTime(Instant.now())
                .data(chatApplicationService.history(identity()))
                .build();
        send(history);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        try {
            WebsocketMessageDTO incoming = JSON.parseObject(message, WebsocketMessageDTO.class);
            chatApplicationService.handle(incoming, identity(), session.getId()).ifPresent(this::sendUnchecked);
        } catch (RuntimeException exception) {
            log.debug("Rejected WebSocket frame for session {}", session.getId(), exception);
            send(WebsocketMessageDTO.builder()
                    .type(com.wzh.blog.enums.ChatTypeEnum.CHAT_ERROR.getType())
                    .version(1)
                    .eventId(UUID.randomUUID().toString())
                    .serverTime(Instant.now())
                    .data(Map.of("code", "INVALID_MESSAGE", "message", "消息格式或内容不合法"))
                    .build());
        }
    }

    @OnClose
    public void onClose() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (session != null && registered) {
            connectionRegistry.unregister(session.getId());
            chatApplicationService.unregisterSession(session.getId());
            registered = false;
        }
    }

    @jakarta.websocket.OnError
    public void onError(Session session, Throwable error) {
        log.debug("WebSocket session {} failed", session == null ? "" : session.getId(), error);
        onClose();
    }

    private ChatUserIdentity identity() {
        return new ChatUserIdentity(userId, nickname, avatar, clientIpAddress, clientIpSource, clientToken);
    }

    private void sendUnchecked(WebsocketMessageDTO message) {
        try {
            send(message);
        } catch (IOException exception) {
            log.debug("Unable to send WebSocket response", exception);
        }
    }

    private void send(WebsocketMessageDTO message) throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            session.getBasicRemote().sendText(JSON.toJSONString(message));
        }
    }
}
