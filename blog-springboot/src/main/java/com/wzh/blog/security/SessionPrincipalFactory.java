package com.wzh.blog.security;

import com.wzh.blog.dto.UserDetailDTO;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** 将密码校验主体替换为无密码会话主体。 */
public final class SessionPrincipalFactory {

    private SessionPrincipalFactory() {
    }

    public static AuthenticatedUserPrincipal from(UserDetailDTO credentialedUser) {
        return AuthenticatedUserPrincipal.from(credentialedUser);
    }

    public static Authentication passwordFree(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailDTO user)) {
            return authentication;
        }
        AuthenticatedUserPrincipal principal = from(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }
}
