package com.wzh.blog.config;
import com.wzh.blog.dao.StorageProviderConfigDao;

import com.wzh.blog.handler.PageableHandlerInterceptor;
import com.wzh.blog.handler.WebSecurityHandler;
import com.wzh.blog.web.PaginationContext;
import com.wzh.blog.service.RateLimitStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * web mvc配置
 *
 * @author yezhiqiu
 * @date 2021/07/29
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PaginationContext paginationContext;
    private final RateLimitStore rateLimitStore;
    private final StorageProviderConfigDao storageConfigDao;

    public WebMvcConfig(PaginationContext paginationContext,
                        RateLimitStore rateLimitStore,
                        StorageProviderConfigDao storageConfigDao) {
        this.paginationContext = paginationContext;
        this.rateLimitStore = rateLimitStore;
        this.storageConfigDao = storageConfigDao;
    }

    @org.springframework.beans.factory.annotation.Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public WebSecurityHandler getWebSecurityHandler() {
        return new WebSecurityHandler(rateLimitStore);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedHeaders("Content-Type", "X-XSRF-TOKEN", "X-Requested-With", "Authorization")
                .allowedOrigins(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PageableHandlerInterceptor(paginationContext));
        registry.addInterceptor(getWebSecurityHandler());
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("classpath:/managed-storage-placeholder/")
                .resourceChain(false)
                .addResolver(new ManagedLocalStorageResourceResolver(storageConfigDao));
    }


}
