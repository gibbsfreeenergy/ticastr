package com.wzh.blog.config;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedLocalStorageResourceResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesUploadsFromTheDatabaseSelectedLocalProfileInsteadOfEnvironmentProperties() throws Exception {
        Path managedRoot = Files.createDirectories(temporaryDirectory.resolve("managed"));
        Files.writeString(managedRoot.resolve("image.png"), "managed");
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        when(configDao.selectActive()).thenReturn(StorageProviderConfig.builder().provider("local")
                .localRoot(managedRoot.toString()).publicUrl("/uploads/").build());

        ManagedLocalStorageResourceResolver resolver = new ManagedLocalStorageResourceResolver(configDao);

        org.springframework.core.io.Resource resource = resolver.resolveResource(null, "image.png", List.of(), null);

        assertThat(resource).isNotNull();
        assertThat(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("managed");
        assertThat(resolver.resolveResource(null, "../outside.png", List.of(), null)).isNull();
        verify(configDao, org.mockito.Mockito.times(2)).selectActive();
    }

    @Test
    void doesNotServeAPathWhenTheDatabaseSelectedProfileIsCloudOrThePathEscapesItsRoot() {
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        when(configDao.selectActive()).thenReturn(StorageProviderConfig.builder().provider("oss").build());
        ManagedLocalStorageResourceResolver resolver = new ManagedLocalStorageResourceResolver(configDao);

        assertThat(resolver.resolveResource(null, "../../secrets.txt", List.of(), null)).isNull();
        assertThat(resolver.resolveResource(null, "image.png", List.of(), null)).isNull();
    }
}
