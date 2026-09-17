package com.wzh.blog.handler;

import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.dto.ResourceRoleDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicAuthorizationManagerTest {

    @Test
    void invalidationCannotBeOverwrittenByAnOlderInFlightLoad() throws Exception {
        var queryStarted = new CountDownLatch(1);
        var finishOldQuery = new CountDownLatch(1);
        var invalidationStarted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        RoleDao dao = mock(RoleDao.class);
        when(dao.listResourceRoles()).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                queryStarted.countDown();
                if (!finishOldQuery.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
                return List.of(resource("/admin/articles", false, List.of("OLD_ROLE")));
            }
            return List.of(resource("/admin/articles", false, List.of("NEW_ROLE")));
        });
        var source = new FilterInvocationSecurityMetadataSourceImpl(dao);
        var request = new MockHttpServletRequest("GET", "/admin/articles");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var loading = executor.submit(() -> source.findRequiredRoles(request));
            assertThat(queryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var invalidating = executor.submit(() -> {
                invalidationStarted.countDown();
                source.clearDataSource();
            });
            try {
                assertThat(invalidationStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> invalidating.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                finishOldQuery.countDown();
            }
            loading.get(5, TimeUnit.SECONDS);
            invalidating.get(5, TimeUnit.SECONDS);
        } finally {
            finishOldQuery.countDown();
        }
        assertThat(source.findRequiredRoles(request).orElseThrow()).containsExactly("NEW_ROLE");
    }

    @Test
    void deniesAnUnregisteredEndpoint() {
        assertThat(decisionFor(managerWith(List.of()), "/unexpected", List.of("ROLE_ADMIN"))).isFalse();
    }

    @Test
    void allowsAnExplicitlyAnonymousEndpoint() {
        assertThat(decisionFor(managerWith(List.of(resource("/articles", true, List.of()))), "/articles", List.of())).isTrue();
    }

    @Test
    void requiresTheMappedRoleForProtectedEndpoints() {
        DynamicAuthorizationManager manager = managerWith(List.of(resource("/admin/articles", false, List.of("ROLE_ADMIN"))));

        assertThat(decisionFor(manager, "/admin/articles", List.of("ROLE_USER"))).isFalse();
        assertThat(decisionFor(manager, "/admin/articles", List.of("ROLE_ADMIN"))).isTrue();
    }

    @Test
    void retriesAfterAFailedMetadataLoadInsteadOfCachingFailureAsAnEmptyMap() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RoleDao roleDao = (RoleDao) Proxy.newProxyInstance(
                RoleDao.class.getClassLoader(),
                new Class<?>[]{RoleDao.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("listResourceRoles")) {
                        return null;
                    }
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("database unavailable");
                    }
                    return List.of(resource("/articles", true, List.of()));
                });

        FilterInvocationSecurityMetadataSourceImpl source = new FilterInvocationSecurityMetadataSourceImpl(roleDao);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/articles");
        assertThat(source.findRequiredRoles(request)).isEmpty();

        Field lastLoadAttempt = FilterInvocationSecurityMetadataSourceImpl.class
                .getDeclaredField("lastLoadAttemptMillis");
        lastLoadAttempt.setAccessible(true);
        lastLoadAttempt.setLong(source, System.currentTimeMillis() - 31_000);
        assertThat(source.findRequiredRoles(request)).isPresent();
        assertThat(attempts).hasValue(2);
    }

    private DynamicAuthorizationManager managerWith(List<ResourceRoleDTO> resources) {
        RoleDao roleDao = (RoleDao) Proxy.newProxyInstance(
                RoleDao.class.getClassLoader(),
                new Class<?>[]{RoleDao.class},
                (proxy, method, args) -> method.getName().equals("listResourceRoles") ? resources : null);
        return new DynamicAuthorizationManager(new FilterInvocationSecurityMetadataSourceImpl(roleDao));
    }

    private ResourceRoleDTO resource(String url, boolean anonymous, List<String> roles) {
        ResourceRoleDTO resource = new ResourceRoleDTO();
        resource.setUrl(url);
        resource.setRequestMethod("GET");
        resource.setAnonymous(anonymous);
        resource.setRoleList(roles);
        return resource;
    }

    private boolean decisionFor(DynamicAuthorizationManager manager, String path, List<String> roles) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        RequestAuthorizationContext context = new RequestAuthorizationContext(request);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "user",
                "password",
                roles.stream().map(SimpleGrantedAuthority::new).toList());
        return ((AuthorizationDecision) manager.authorize(() -> authentication, context)).isGranted();
    }
}
