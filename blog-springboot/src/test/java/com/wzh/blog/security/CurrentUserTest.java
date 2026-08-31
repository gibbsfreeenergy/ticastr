package com.wzh.blog.security;

import com.wzh.blog.dto.UserDetailDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void treatsAnonymousAndForeignPrincipalsAsUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(currentUser.find()).isEmpty();
        assertThatThrownBy(currentUser::require).isInstanceOf(RuntimeException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("foreign-principal", null));
        assertThat(currentUser.find()).isEmpty();
    }

    @Test
    void exposesOnlyTheApplicationPrincipal() {
        UserDetailDTO principal = UserDetailDTO.builder()
                .id(7)
                .userInfoId(42)
                .username("user@example.com")
                .password("hash")
                .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(principal, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(currentUser.require()).isSameAs(principal);
        assertThat(currentUser.id()).isEqualTo(42);
    }
}
