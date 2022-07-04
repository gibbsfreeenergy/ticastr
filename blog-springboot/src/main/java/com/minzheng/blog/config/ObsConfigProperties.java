package com.minzheng.blog.config;


import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.context.annotation.Configuration;
@Data
@Configuration
@ConfigurationProperties(prefix = "upload.obs")
public class ObsConfigProperties {


    /**
     * obs域名
     */
    private String url;

    /**
     * 访问密钥id
     */
    private String accessKey;


    /**
     * 访问密钥密码
     */
    private String accessKeySecret;

    /**
     * 终点路由
     */
    private String endPoint;

    /**
     * bucket名称
     */
    private String bucketName;

}