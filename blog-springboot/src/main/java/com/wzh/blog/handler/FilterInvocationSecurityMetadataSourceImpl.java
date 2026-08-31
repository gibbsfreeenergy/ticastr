package com.wzh.blog.handler;

import com.wzh.blog.dao.RoleDao;
import com.wzh.blog.dto.ResourceRoleDTO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Resolves URL permissions from the database for Spring Security 7. */
@Component
@Log4j2
public class FilterInvocationSecurityMetadataSourceImpl {

    private static final long FAILED_RELOAD_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    /** null means not loaded or the last load failed; an empty list is valid. */
    private volatile List<ResourceRoleDTO> resourceRoleList;
    private volatile long lastLoadAttemptMillis;
    private volatile long version;
    private final ReentrantLock loadLock = new ReentrantLock();
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    private final RoleDao roleDao;

    public FilterInvocationSecurityMetadataSourceImpl(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    @PostConstruct
    private void loadDataSource() {
        long now = System.currentTimeMillis();
        if (resourceRoleList != null || now - lastLoadAttemptMillis < FAILED_RELOAD_INTERVAL_MILLIS) {
            return;
        }
        if (!loadLock.tryLock()) {
            return;
        }
        try {
            lastLoadAttemptMillis = now;
            List<ResourceRoleDTO> loaded = roleDao.listResourceRoles();
            resourceRoleList = loaded == null ? List.of() : List.copyOf(loaded);
            version++;
        } catch (RuntimeException exception) {
            // Fail closed for protected URLs, but retain the unloaded marker so
            // a later request can retry after the bounded failure window.
            resourceRoleList = null;
            log.warn("Unable to load dynamic authorization metadata; requests fail closed", exception);
        } finally {
            loadLock.unlock();
        }
    }

    public void clearDataSource() {
        resourceRoleList = null;
        lastLoadAttemptMillis = 0;
    }

    public long version() {
        return version;
    }

    /**
     * Returns an empty optional only when the URL has not been registered.
     * An empty role collection represents an explicitly public URL; the
     * "disable" role represents a registered, but unassigned, URL.
     */
    public Optional<Collection<String>> findRequiredRoles(HttpServletRequest request) {
        if (resourceRoleList == null) {
            loadDataSource();
        }
        List<ResourceRoleDTO> snapshot = resourceRoleList;
        if (snapshot == null) {
            return Optional.empty();
        }
        String method = request.getMethod();
        String url = request.getRequestURI();
        for (ResourceRoleDTO resourceRoleDTO : snapshot) {
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
