package com.wzh.blog.security;

import com.wzh.blog.exception.BizException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.wzh.blog.enums.StatusCodeEnum.NO_LOGIN;

/**
 * Single boundary for resolving the authenticated application principal.
 * Anonymous and foreign principals are deliberately treated as anonymous.
 */
@Component
public class CurrentUser {

    public Optional<AuthenticatedUserPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUserPrincipal user
                ? Optional.of(user)
                : Optional.empty();
    }

    public AuthenticatedUserPrincipal require() {
        return find().orElseThrow(() -> new BizException(NO_LOGIN));
    }

    public Integer id() {
        return require().getUserInfoId();
    }
}
