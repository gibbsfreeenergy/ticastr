package com.wzh.blog.strategy;

import org.springframework.web.multipart.MultipartFile;

/**
 * 上传策略
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
public interface UploadStrategy {

    /**
     * 上传文件
     *
     * @param file 文件
     * @param path 上传路径
     * @return {@link String} 文件地址
     */
    String uploadFile(MultipartFile file, String path);

    /** Builds the public reference for a provider-relative object key. */
    default String getFileAccessUrl(String filePath) {
        return filePath;
    }

    /** Deletes an object by its provider-relative path when supported. */
    default void deleteFile(String filePath) {
        // Legacy providers may opt in; the context exposes one lifecycle API.
    }

    default Boolean exists(String filePath) {
        return false;
    }

}
