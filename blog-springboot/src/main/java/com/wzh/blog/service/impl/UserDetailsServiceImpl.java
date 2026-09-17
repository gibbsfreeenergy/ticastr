package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.dto.UserDetailDTO;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.exception.BizException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员登录详情服务。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserAuthDao userAuthDao;
    private final UserInfoDao userInfoDao;
    private final RoleDao roleDao;

    public UserDetailsServiceImpl(UserAuthDao userAuthDao,
                                  UserInfoDao userInfoDao,
                                  RoleDao roleDao) {
        this.userAuthDao = userAuthDao;
        this.userInfoDao = userInfoDao;
        this.roleDao = roleDao;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            throw new BizException("用户名不能为空！");
        }
        UserAuth userAuth = userAuthDao.selectOne(new LambdaQueryWrapper<UserAuth>()
                .select(UserAuth::getId, UserAuth::getUserInfoId, UserAuth::getUsername,
                        UserAuth::getPassword, UserAuth::getLoginType)
                .eq(UserAuth::getUsername, username));
        if (userAuth == null) {
            throw new BizException("用户名不存在!");
        }
        return convertUserDetail(userAuth);
    }

    public UserDetailDTO convertUserDetail(UserAuth user) {
        UserInfo userInfo = userInfoDao.selectById(user.getUserInfoId());
        if (userInfo == null) {
            throw new BizException("管理员资料不存在");
        }
        List<String> roleList = roleDao.listRolesByUserInfoId(userInfo.getId());
        return UserDetailDTO.builder()
                .id(user.getId())
                .loginType(user.getLoginType())
                .userInfoId(userInfo.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .email(userInfo.getEmail())
                .roleList(roleList)
                .nickname(userInfo.getNickname())
                .avatar(userInfo.getAvatar())
                .intro(userInfo.getIntro())
                .webSite(userInfo.getWebSite())
                .isDisable(userInfo.getIsDisable())
                .build();
    }
}
