package com.wzh.blog.bootstrap;

import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.dao.UserRoleDao;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.entity.UserRole;
import com.wzh.blog.enums.RoleEnum;
import com.wzh.blog.service.RoleLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {

    private final UserInfoDao userInfoDao = mock(UserInfoDao.class);
    private final UserAuthDao userAuthDao = mock(UserAuthDao.class);
    private final UserRoleDao userRoleDao = mock(UserRoleDao.class);
    private final RoleLookupService roleLookupService = mock(RoleLookupService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void createsAdministratorWithEncodedPasswordAndRole() throws Exception {
        when(userAuthDao.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("a-secure-password")).thenReturn("encoded");
        when(roleLookupService.requireRoleId(RoleEnum.ADMIN)).thenReturn(7);
        doAnswer(invocation -> {
            invocation.getArgument(0, UserInfo.class).setId(42);
            return 1;
        }).when(userInfoDao).insert(any(UserInfo.class));
        AdminBootstrapRunner runner = runner("admin@example.com", "a-secure-password");

        runner.run(mock(ApplicationArguments.class));

        var authCaptor = org.mockito.ArgumentCaptor.forClass(UserAuth.class);
        verify(userAuthDao).insert(authCaptor.capture());
        assertThat(authCaptor.getValue().getUserInfoId()).isEqualTo(42);
        assertThat(authCaptor.getValue().getPassword()).isEqualTo("encoded");
        var roleCaptor = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleDao).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getUserId()).isEqualTo(42);
        assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(7);
    }

    @Test
    void skipsExistingAccount() throws Exception {
        when(userAuthDao.selectCount(any())).thenReturn(1L);

        runner("admin@example.com", "a-secure-password").run(mock(ApplicationArguments.class));

        verify(userInfoDao, never()).insert(any(UserInfo.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejectsPlaceholderCredentialsBeforeAccessingDatabase() {
        AdminBootstrapRunner runner = runner("admin@example.com", "change-me");

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters");
        verify(userAuthDao, never()).selectCount(any());
    }

    private AdminBootstrapRunner runner(String username, String password) {
        return new AdminBootstrapRunner(
                userInfoDao,
                userAuthDao,
                userRoleDao,
                roleLookupService,
                passwordEncoder,
                username,
                password,
                "Administrator"
        );
    }
}
