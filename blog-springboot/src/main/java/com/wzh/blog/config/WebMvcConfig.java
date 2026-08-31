package com.wzh.blog.config;


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
    private final StorageProperties storageProperties;

    public WebMvcConfig(PaginationContext paginationContext,
                        RateLimitStore rateLimitStore,
                        StorageProperties storageProperties) {
        this.paginationContext = paginationContext;
        this.rateLimitStore = rateLimitStore;
        this.storageProperties = storageProperties;
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
        String localRoot = java.nio.file.Path.of(storageProperties.getLocalRoot())
                .toAbsolutePath()
                .normalize()
                .toString();
        String normalizedPath = localRoot.endsWith("\\") || localRoot.endsWith("/")
                ? localRoot
                : localRoot + java.io.File.separator;
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + normalizedPath);
    }


}
