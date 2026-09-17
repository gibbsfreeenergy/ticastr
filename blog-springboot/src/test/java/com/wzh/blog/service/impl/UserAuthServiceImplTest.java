package com.wzh.blog.service.impl;

import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.vo.PasswordVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplTest {

    @InjectMocks
    private UserAuthServiceImpl userAuthService;

    @Mock
    private UserAuthDao userAuthDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private AuthenticatedUserPrincipal principal;

    @Test
    void updatesTheAuthenticatedAdministratorPassword() {
        UserAuth account = UserAuth.builder().id(7).password("old-hash").build();
        when(currentUser.require()).thenReturn(principal);
        when(principal.getId()).thenReturn(7);
        when(userAuthDao.selectOne(any())).thenReturn(account);
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        userAuthService.updateAdminPassword(PasswordVO.builder()
                .oldPassword("old-password")
                .newPassword("new-password")
                .build());

        ArgumentCaptor<UserAuth> captured = ArgumentCaptor.forClass(UserAuth.class);
        verify(userAuthDao).updateById(captured.capture());
        assertThat(captured.getValue().getId()).isEqualTo(7);
        assertThat(captured.getValue().getPassword()).isEqualTo("new-hash");
    }
}
