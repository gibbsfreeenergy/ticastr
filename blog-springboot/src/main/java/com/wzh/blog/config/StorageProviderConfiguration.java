package com.wzh.blog.config;

import com.wzh.blog.infrastructure.storage.CosStorageProvider;
import com.wzh.blog.infrastructure.storage.LocalStorageProvider;
import com.wzh.blog.infrastructure.storage.OssStorageProvider;
import com.wzh.blog.infrastructure.storage.TosStorageProvider;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class StorageProviderConfiguration {

    @Bean
    public LocalStorageProvider localStorageProvider(StorageProperties properties) {
        return new LocalStorageProvider(Path.of(properties.getLocalRoot()));
    }

    @Bean
    public OssStorageProvider ossStorageProvider(StorageProperties properties) {
        return new OssStorageProvider(properties);
    }

    @Bean
    public CosStorageProvider cosStorageProvider(StorageProperties properties) {
        return new CosStorageProvider(properties);
    }

    @Bean
    public TosStorageProvider tosStorageProvider(StorageProperties properties) {
        return new TosStorageProvider(properties);
    }

    @Bean
    public StorageProviderRegistry storageProviderRegistry(
            StorageProperties properties,
            List<StorageProvider> providers) {
        return new StorageProviderRegistry(
                StorageProviderType.from(properties.getActiveProvider()),
                providers,
                properties);
    }
}
