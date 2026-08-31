package com.wzh.blog.service;

/** Typed port for broadcasting authorization metadata invalidation. */
public interface AuthorizationInvalidationPublisher {

    void publish();
}
