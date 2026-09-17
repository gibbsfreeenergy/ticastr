package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.UserAuthService;
import com.wzh.blog.vo.PasswordVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员账号服务实现。
 */
@Service
public class UserAuthServiceImpl extends ServiceImpl<UserAuthDao, UserAuth> implements UserAuthService {

    private final UserAuthDao userAuthDao;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserAuthServiceImpl(UserAuthDao userAuthDao,
                               PasswordEncoder passwordEncoder,
                               CurrentUser currentUser) {
        this.userAuthDao = userAuthDao;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminPassword(PasswordVO passwordVO) {
        UserAuth user = userAuthDao.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getId, currentUser.require().getId()));
        if (user == null || !passwordEncoder.matches(passwordVO.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码不正确");
        }
        userAuthDao.updateById(UserAuth.builder()
                .id(user.getId())
                .password(passwordEncoder.encode(passwordVO.getNewPassword()))
                .build());
    }
}
