package com.wzh.blog.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.dao.UserRoleDao;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.entity.UserRole;
import com.wzh.blog.enums.LoginTypeEnum;
import com.wzh.blog.enums.RoleEnum;
import com.wzh.blog.service.RoleLookupService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String DEFAULT_AVATAR = "https://static.talkxj.com/avatar/user.png";

    private final UserInfoDao userInfoDao;
    private final UserAuthDao userAuthDao;
    private final UserRoleDao userRoleDao;
    private final RoleLookupService roleLookupService;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String nickname;

    public AdminBootstrapRunner(UserInfoDao userInfoDao,
                                UserAuthDao userAuthDao,
                                UserRoleDao userRoleDao,
                                RoleLookupService roleLookupService,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap-admin.username:}") String username,
                                @Value("${app.bootstrap-admin.password:}") String password,
                                @Value("${app.bootstrap-admin.nickname:Administrator}") String nickname) {
        this.userInfoDao = userInfoDao;
        this.userAuthDao = userAuthDao;
        this.userRoleDao = userRoleDao;
        this.roleLookupService = roleLookupService;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.nickname = nickname == null || nickname.isBlank() ? "Administrator" : nickname.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();
        Long existing = userAuthDao.selectCount(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUsername, username)
                .eq(UserAuth::getLoginType, LoginTypeEnum.EMAIL.getType()));
        if (existing > 0) {
            return;
        }

        UserInfo userInfo = UserInfo.builder()
                .email(username)
                .nickname(nickname)
                .avatar(DEFAULT_AVATAR)
                .isDisable(0)
                .build();
        userInfoDao.insert(userInfo);

        userAuthDao.insert(UserAuth.builder()
                .userInfoId(userInfo.getId())
                .username(username)
                .password(passwordEncoder.encode(password))
                .loginType(LoginTypeEnum.EMAIL.getType())
                .build());
        userRoleDao.insert(UserRole.builder()
                .userId(userInfo.getId())
                .roleId(roleLookupService.requireRoleId(RoleEnum.ADMIN))
                .build());
    }

    private void validateConfiguration() {
        if (username.isBlank() || !username.contains("@")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME must be a valid email address");
        }
        if (password.length() < 12 || "change-me".equalsIgnoreCase(password)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters and cannot be a placeholder");
        }
    }
}
