package com.wzh.blog.security;

import com.wzh.blog.dto.UserDetailDTO;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Converts authentication-phase details into the password-free session principal. */
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
