package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static com.wzh.blog.constant.RedisPrefixConst.USER_CODE_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAuth.class);
    }

    @InjectMocks
    private UserAuthServiceImpl userAuthService;

    @Mock
    private RedisService redisService;
    @Mock
    private UserAuthDao userAuthDao;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void passwordResetConsumesTheVerificationCode() {
        UserVO user = UserVO.builder()
                .username("reader@example.com")
                .password("new-password")
                .code("123456")
                .build();
        when(redisService.consumeIfEquals(USER_CODE_KEY + user.getUsername(), user.getCode())).thenReturn(true);
        when(userAuthDao.selectOne(any())).thenReturn(UserAuth.builder().username(user.getUsername()).build());
        when(passwordEncoder.encode(user.getPassword())).thenReturn("encoded-password");

        userAuthService.updatePassword(user);

        verify(redisService).consumeIfEquals(USER_CODE_KEY + user.getUsername(), user.getCode());
        verify(userAuthDao).update(any(UserAuth.class), any(LambdaUpdateWrapper.class));
    }
}
