package com.wzh.blog.security;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.UserDetailDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionPrincipalFactoryTest {

    @Test
    void sessionPrincipalContainsIdentityAndNoCredentialField() {
        UserDetailDTO credentialed = UserDetailDTO.builder()
                .id(1)
                .userInfoId(2)
                .username("admin")
                .password("hash")
                .roleList(List.of("admin"))
                .isDisable(0)
                .build();

        AuthenticatedUserPrincipal principal = SessionPrincipalFactory.from(credentialed);

        assertEquals(2, principal.getUserInfoId());
        assertEquals("admin", principal.getUsername());
        assertFalse(principal.disabled());
        assertTrue(principal.getAuthorities().stream().anyMatch(authority -> "admin".equals(authority.getAuthority())));
        assertTrue(java.util.Arrays.stream(AuthenticatedUserPrincipal.class.getDeclaredFields())
                .map(Field::getName)
                .noneMatch(name -> name.toLowerCase().contains("password")));
        assertFalse(JSON.toJSONString(principal).toLowerCase().contains("hash"));
        assertFalse(JSON.toJSONString(principal).toLowerCase().contains("password"));
    }

    @Test
    void authenticationReplacementDoesNotKeepCredentialedUserDetails() {
        UserDetailDTO credentialed = UserDetailDTO.builder()
                .id(1).userInfoId(2).username("admin").password("hash").isDisable(0).build();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                credentialed, "hash", credentialed.getAuthorities());

        Authentication replaced = SessionPrincipalFactory.passwordFree(authentication);

        assertInstanceOf(AuthenticatedUserPrincipal.class, replaced.getPrincipal());
        assertNull(replaced.getCredentials());
        assertNull(((AuthenticatedUserPrincipal) replaced.getPrincipal()).getPassword());
    }
}
