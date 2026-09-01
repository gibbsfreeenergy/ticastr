package com.wzh.blog.config;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProviderType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** Resolves local uploads from the current database-selected profile for each request. */
class ManagedLocalStorageResourceResolver implements ResourceResolver {

    private final StorageProviderConfigDao configDao;

    ManagedLocalStorageResourceResolver(StorageProviderConfigDao configDao) {
        this.configDao = configDao;
    }

    @Override
    public Resource resolveResource(HttpServletRequest request,
                                    String requestPath,
                                    List<? extends Resource> locations,
                                    ResourceResolverChain chain) {
        StorageProviderConfig active = configDao.selectActive();
        if (!isActiveLocalProfile(active) || requestPath == null || requestPath.isBlank()) {
            return null;
        }
        try {
            Path root = Path.of(active.getLocalRoot()).toRealPath();
            Path candidate = root.resolve(requestPath).normalize();
            if (!Files.isRegularFile(candidate)) {
                return null;
            }
            Path resolved = candidate.toRealPath();
            return resolved.startsWith(root) ? new FileSystemResource(resolved) : null;
        } catch (IOException | InvalidPathException ignored) {
            return null;
        }
    }

    @Override
    public String resolveUrlPath(String resourcePath,
                                 List<? extends Resource> locations,
                                 ResourceResolverChain chain) {
        return null;
    }

    private boolean isActiveLocalProfile(StorageProviderConfig config) {
        return config != null && StorageProviderType.LOCAL.code().equalsIgnoreCase(config.getProvider())
                && config.getLocalRoot() != null && !config.getLocalRoot().isBlank();
    }
}
