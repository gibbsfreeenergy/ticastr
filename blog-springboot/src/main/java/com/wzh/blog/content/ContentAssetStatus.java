package com.wzh.blog.content;

/** Lifecycle states for immutable article content objects. */
public enum ContentAssetStatus {
    PENDING,
    ACTIVE,
    RETIRED,
    DELETED,
    DELETE_FAILED
}
