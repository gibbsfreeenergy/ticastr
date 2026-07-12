package com.wzh.blog.handler;

import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.dto.ResourceRoleDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicAuthorizationManagerTest {

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
