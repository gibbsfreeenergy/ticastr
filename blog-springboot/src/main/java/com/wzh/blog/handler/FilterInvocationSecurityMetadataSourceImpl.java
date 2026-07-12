package com.wzh.blog.handler;

import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.dto.ResourceRoleDTO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Resolves URL permissions from the database for Spring Security 7. */
@Component
public class FilterInvocationSecurityMetadataSourceImpl {

    private volatile List<ResourceRoleDTO> resourceRoleList = List.of();

    private final RoleDao roleDao;

    public FilterInvocationSecurityMetadataSourceImpl(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    @PostConstruct
    private void loadDataSource() {
        resourceRoleList = List.copyOf(roleDao.listResourceRoles());
    }

    public void clearDataSource() {
        resourceRoleList = List.of();
    }

    /**
     * Returns an empty optional only when the URL has not been registered.
     * An empty role collection represents an explicitly public URL; the
     * "disable" role represents a registered, but unassigned, URL.
     */
    public Optional<Collection<String>> findRequiredRoles(HttpServletRequest request) {
        if (CollectionUtils.isEmpty(resourceRoleList)) {
            loadDataSource();
        }
        String method = request.getMethod();
        String url = request.getRequestURI();
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        for (ResourceRoleDTO resourceRoleDTO : resourceRoleList) {
            if (resourceRoleDTO.getUrl() != null
                    && resourceRoleDTO.getRequestMethod() != null
                    && antPathMatcher.match(resourceRoleDTO.getUrl(), url)
                    && resourceRoleDTO.getRequestMethod().equals(method)) {
                if (Boolean.TRUE.equals(resourceRoleDTO.getAnonymous())) {
                    return Optional.of(List.of());
                }
                List<String> roleList = resourceRoleDTO.getRoleList();
                return Optional.of(CollectionUtils.isEmpty(roleList) ? List.of("disable") : roleList);
            }
        }
        return Optional.empty();
    }
}
