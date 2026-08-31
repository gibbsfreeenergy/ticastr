package com.wzh.blog.handler;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.dto.UserInfoDTO;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.dto.UserDetailDTO;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.Result;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.wzh.blog.constant.CommonConst.APPLICATION_JSON;


/**
 * 登录成功处理
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Component
public class AuthenticationSuccessHandlerImpl implements AuthenticationSuccessHandler {
    private final UserAuthDao userAuthDao;
    private final CurrentUser currentUser;

    public AuthenticationSuccessHandlerImpl(UserAuthDao userAuthDao, CurrentUser currentUser) {
        this.userAuthDao = userAuthDao;
        this.currentUser = currentUser;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) throws IOException {
        UserDetailDTO credentialedUser = authentication.getPrincipal() instanceof UserDetailDTO user
                ? user : null;
        AuthenticatedUserPrincipal loginUser = credentialedUser == null
                ? currentUser.require()
                : AuthenticatedUserPrincipal.from(credentialedUser);
        Authentication passwordFreeAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(passwordFreeAuthentication);
        UserInfoDTO userLoginDTO = BeanCopyUtils.copyObject(loginUser, UserInfoDTO.class);
        httpServletResponse.setContentType(APPLICATION_JSON);
        httpServletResponse.getWriter().write(JSON.toJSONString(Result.ok(userLoginDTO)));
        // 更新用户ip，最近登录时间
        updateUserInfo(loginUser);
    }

    /**
     * 更新用户信息
     */
    @Async
    public void updateUserInfo(AuthenticatedUserPrincipal loginUser) {
        UserAuth userAuth = UserAuth.builder()
                .id(loginUser.getId())
                .ipAddress(loginUser.getIpAddress())
                .ipSource(loginUser.getIpSource())
                .lastLoginTime(loginUser.getLastLoginTime())
                .build();
        userAuthDao.updateById(userAuth);
    }

}
