package com.wzh.blog.util;

import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.exception.BizException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.wzh.blog.enums.StatusCodeEnum.NO_LOGIN;


/**
 * 用户工具类
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Component
public class UserUtils {

    /**
     * 获取当前登录用户
     *
     * @return 用户登录信息
     */
    public static AuthenticatedUserPrincipal getLoginUser() {
        return findLoginUser().orElse(null);
    }

    /**
     * Resolves only the application's principal.  Spring's anonymous
     * authentication is never exposed as a domain user.
     */
    public static Optional<AuthenticatedUserPrincipal> findLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUserPrincipal user
                ? Optional.of(user)
                : Optional.empty();
    }

    public static AuthenticatedUserPrincipal requireLoginUser() {
        return findLoginUser().orElseThrow(() -> new BizException(NO_LOGIN));
    }

}
