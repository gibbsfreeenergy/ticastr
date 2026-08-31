package com.wzh.blog.media;

import org.springframework.web.multipart.MultipartFile;

/** Application-facing storage port. Provider SDKs must stay behind this interface. */
public interface MediaAssetStore {

    String upload(MultipartFile file, String path);

    void delete(String fileReference);

    boolean exists(String fileReference);
}
