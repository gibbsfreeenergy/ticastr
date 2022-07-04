package com.wzh.blog.strategy.impl;


import com.wzh.blog.config.ObsConfigProperties;

import com.obs.services.ObsClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * @author Gibbsfreeenergy
 * @Title:
 * @Package
 * @Description:
 * @date 2022/6/2122:40
 */
@Service("obsUploadStrategyImpl")
@Slf4j
public class ObsUploadStrategyImpl extends AbstractUploadStrategyImpl{

    @Autowired
    private ObsConfigProperties obsConfigProperties;

    @Override
    public Boolean exists(String filePath) {
        return getObsClient().doesObjectExist(obsConfigProperties.getBucketName(), filePath);
    }

    @Override
    public void upload(String path, String fileName, InputStream inputStream){
        getObsClient().putObject(obsConfigProperties.getBucketName(), path + fileName, inputStream);
    }

    @Override
    public String getFileAccessUrl(String filePath) {
        return obsConfigProperties.getUrl() + filePath;
    }
    /**
     * 获取ossClient
     *
     * @return {@link ObsClient} ossClient
     */
    public ObsClient getObsClient(){
        return new ObsClient(obsConfigProperties.getAccessKey(), obsConfigProperties.getAccessKeySecret(),obsConfigProperties.getEndPoint());
    }
}