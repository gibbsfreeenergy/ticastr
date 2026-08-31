package com.wzh.blog.vo;

/** Safe result of an ephemeral provider write/read/delete check. */
public record StorageProviderValidationVO(
        String provider,
        boolean success,
        boolean write,
        boolean read,
        boolean delete,
        String message) {
}
