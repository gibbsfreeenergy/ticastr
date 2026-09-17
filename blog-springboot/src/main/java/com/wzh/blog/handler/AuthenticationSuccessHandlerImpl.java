package com.wzh.blog.handler;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.UserDetailDTO;
import com.wzh.blog.dto.UserInfoDTO;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.vo.Result;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.wzh.blog.constant.CommonConst.APPLICATION_JSON;

/**
 * 管理员登录成功处理。
 */
@Component
public class AuthenticationSuccessHandlerImpl implements AuthenticationSuccessHandler {

    private final CurrentUser currentUser;

    public AuthenticationSuccessHandlerImpl(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UserDetailDTO credentialedUser = authentication.getPrincipal() instanceof UserDetailDTO user
                ? user : null;
        AuthenticatedUserPrincipal loginUser = credentialedUser == null
                ? currentUser.require()
                : AuthenticatedUserPrincipal.from(credentialedUser);
        Authentication passwordFree = UsernamePasswordAuthenticationToken.authenticated(
                loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(passwordFree);
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .id(loginUser.getId())
                .userInfoId(loginUser.getUserInfoId())
                .email(loginUser.getEmail())
                .loginType(loginUser.getLoginType())
                .username(loginUser.getUsername())
                .roleList(loginUser.getRoleList())
                .nickname(loginUser.getNickname())
                .avatar(loginUser.getAvatar())
                .intro(loginUser.getIntro())
                .webSite(loginUser.getWebSite())
                .build();
        response.setContentType(APPLICATION_JSON);
        response.getWriter().write(JSON.toJSONString(Result.ok(userInfo)));
    }
}
