package com.wzh.blog.config;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.infrastructure.storage.StorageProviderFactory;
import com.wzh.blog.media.StorageProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageProviderConfiguration {

    @Bean
    public StorageProviderFactory storageProviderFactory(StorageConfigCrypto crypto) {
        return new StorageProviderFactory(crypto);
    }

    @Bean
    public StorageProviderRegistry storageProviderRegistry(
            StorageProviderConfigDao configDao,
            StorageProviderFactory factory) {
        return new StorageProviderRegistry(configDao, factory);
    }
}
