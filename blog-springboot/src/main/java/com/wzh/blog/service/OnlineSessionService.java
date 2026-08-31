package com.wzh.blog.service;

import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.dto.UserOnlineDTO;
import com.wzh.blog.entity.UserAuth;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries the actual Spring Session repository instead of the JVM-local
 * SessionRegistry.  This keeps online-user and forced-logout behavior
 * consistent when more than one API node is running.
 */
@Service
public class OnlineSessionService {

    private static final String SECURITY_CONTEXT_KEY =
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final UserAuthDao userAuthDao;
    private final SessionRegistry sessionRegistry;

    public OnlineSessionService(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessionRepository,
                                UserAuthDao userAuthDao,
                                SessionRegistry sessionRegistry) {
        this.sessionRepository = sessionRepository.getIfAvailable();
        this.userAuthDao = userAuthDao;
        this.sessionRegistry = sessionRegistry;
    }

    public List<UserOnlineDTO> list(String keywords) {
        Map<Integer, UserOnlineDTO> users = new LinkedHashMap<>();
        if (sessionRepository == null) {
            return listLocalSessions(keywords);
        }
        for (UserAuth account : listAccounts()) {
            if (account.getUsername() == null) {
                continue;
            }
            for (Session session : sessionRepository.findByPrincipalName(account.getUsername()).values()) {
                AuthenticatedUserPrincipal principal = principalOf(session);
                if (principal == null || principal.getUserInfoId() == null) {
                    continue;
                }
                if (keywords != null && !keywords.isBlank()
                        && (principal.getNickname() == null || !principal.getNickname().contains(keywords))) {
                    continue;
                }
                users.putIfAbsent(principal.getUserInfoId(), toOnlineUser(principal));
            }
        }
        return users.values().stream()
                .sorted(Comparator.comparing(UserOnlineDTO::getLastLoginTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public int expireForUser(Integer userInfoId) {
        if (sessionRepository == null) {
            return expireLocalSessions(userInfoId);
        }
        int expired = 0;
        for (UserAuth account : listAccounts()) {
            if (!userInfoId.equals(account.getUserInfoId()) || account.getUsername() == null) {
                continue;
            }
            for (String sessionId : sessionRepository.findByPrincipalName(account.getUsername()).keySet()) {
                sessionRepository.deleteById(sessionId);
                expired++;
            }
        }
        return expired;
    }

    private List<UserOnlineDTO> listLocalSessions(String keywords) {
        Map<Integer, UserOnlineDTO> users = new LinkedHashMap<>();
        for (Object candidate : sessionRegistry.getAllPrincipals()) {
            if (!(candidate instanceof AuthenticatedUserPrincipal principal)
                    || principal.getUserInfoId() == null) {
                continue;
            }
            if (keywords != null && !keywords.isBlank()
                    && (principal.getNickname() == null || !principal.getNickname().contains(keywords))) {
                continue;
            }
            users.putIfAbsent(principal.getUserInfoId(), toOnlineUser(principal));
        }
        return users.values().stream()
                .sorted(Comparator.comparing(UserOnlineDTO::getLastLoginTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private int expireLocalSessions(Integer userInfoId) {
        int expired = 0;
        for (Object candidate : sessionRegistry.getAllPrincipals()) {
            if (!(candidate instanceof AuthenticatedUserPrincipal principal)
                    || !userInfoId.equals(principal.getUserInfoId())) {
                continue;
            }
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
                expired++;
            }
        }
        return expired;
    }

    private List<UserAuth> listAccounts() {
        return userAuthDao.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAuth>()
                .select(UserAuth::getUserInfoId, UserAuth::getUsername));
    }

    private AuthenticatedUserPrincipal principalOf(Session session) {
        Object attribute = session.getAttribute(SECURITY_CONTEXT_KEY);
        if (!(attribute instanceof SecurityContext context)) {
            return null;
        }
        Authentication authentication = context.getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal user
                ? user
                : null;
    }

    private UserOnlineDTO toOnlineUser(AuthenticatedUserPrincipal user) {
        return UserOnlineDTO.builder()
                .userInfoId(user.getUserInfoId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .ipAddress(user.getIpAddress())
                .ipSource(user.getIpSource())
                .browser(user.getBrowser())
                .os(user.getOs())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }
}
