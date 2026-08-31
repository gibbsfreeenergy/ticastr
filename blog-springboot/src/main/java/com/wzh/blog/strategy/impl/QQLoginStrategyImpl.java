package com.wzh.blog.strategy.impl;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.config.QQConfigProperties;
import com.wzh.blog.constant.SocialLoginConst;
import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.dao.UserRoleDao;
import com.wzh.blog.dto.QQTokenDTO;
import com.wzh.blog.dto.QQUserInfoDTO;
import com.wzh.blog.dto.SocialUserInfoDTO;
import com.wzh.blog.dto.SocialTokenDTO;
import com.wzh.blog.enums.LoginTypeEnum;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.service.RoleLookupService;
import com.wzh.blog.service.impl.UserDetailsServiceImpl;
import com.wzh.blog.util.CommonUtils;
import com.wzh.blog.vo.QQLoginVO;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.wzh.blog.constant.SocialLoginConst.*;
import static com.wzh.blog.enums.StatusCodeEnum.QQ_LOGIN_ERROR;

/**
 * qq登录策略实现
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Service("qqLoginStrategyImpl")
@Log4j2
public class QQLoginStrategyImpl extends AbstractSocialLoginStrategyImpl {
    private final QQConfigProperties qqConfigProperties;
    private final RestTemplate restTemplate;

    public QQLoginStrategyImpl(QQConfigProperties qqConfigProperties,
                               RestTemplate restTemplate,
                               UserAuthDao userAuthDao,
                               UserInfoDao userInfoDao,
                               UserRoleDao userRoleDao,
                               UserDetailsServiceImpl userDetailsService,
                               RoleLookupService roleLookupService,
                               HttpServletRequest request) {
        super(userAuthDao, userInfoDao, userRoleDao, userDetailsService, roleLookupService, request);
        this.qqConfigProperties = qqConfigProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public SocialTokenDTO getSocialToken(String data) {
        QQLoginVO qqLoginVO = JSON.parseObject(data, QQLoginVO.class);
        // 校验QQ token信息
        checkQQToken(qqLoginVO);
        // 返回token信息
        return SocialTokenDTO.builder()
                .openId(qqLoginVO.getOpenId())
                .accessToken(qqLoginVO.getAccessToken())
                .loginType(LoginTypeEnum.QQ.getType())
                .build();
    }

    @Override
    public SocialUserInfoDTO getSocialUserInfo(SocialTokenDTO socialTokenDTO) {
        // 定义请求参数
        Map<String, String> formData = new HashMap<>(3);
        formData.put(QQ_OPEN_ID, socialTokenDTO.getOpenId());
        formData.put(ACCESS_TOKEN, socialTokenDTO.getAccessToken());
        formData.put(OAUTH_CONSUMER_KEY, qqConfigProperties.getAppId());
        // 获取QQ返回的用户信息
        QQUserInfoDTO qqUserInfoDTO = JSON.parseObject(restTemplate.getForObject(qqConfigProperties.getUserInfoUrl(), String.class, formData), QQUserInfoDTO.class);
        // 返回用户信息
        return SocialUserInfoDTO.builder()
                .nickname(Objects.requireNonNull(qqUserInfoDTO).getNickname())
                .avatar(qqUserInfoDTO.getFigureurl_qq_1())
                .build();
    }

    /**
     * 校验qq token信息
     *
     * @param qqLoginVO qq登录信息
     */
    private void checkQQToken(QQLoginVO qqLoginVO) {
        // 根据token获取qq openId信息
        Map<String, String> qqData = new HashMap<>(1);
        qqData.put(SocialLoginConst.ACCESS_TOKEN, qqLoginVO.getAccessToken());
        try {
            String result = restTemplate.getForObject(qqConfigProperties.getCheckTokenUrl(), String.class, qqData);
            QQTokenDTO qqTokenDTO = JSON.parseObject(CommonUtils.getBracketsContent(Objects.requireNonNull(result)), QQTokenDTO.class);
            // 判断openId是否一致
            if (!qqLoginVO.getOpenId().equals(qqTokenDTO.getOpenid())) {
                throw new BizException(QQ_LOGIN_ERROR);
            }
        } catch (Exception e) {
            log.warn("Unable to validate QQ OAuth token", e);
            throw new BizException(QQ_LOGIN_ERROR);
        }
    }

}
