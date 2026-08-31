package com.wzh.blog.service;

/** Typed Redis port for chat presence; key and TTL policy stay behind it. */
public interface ChatPresenceStore {

    void register(String sessionId);

    void touch(String sessionId);

    void unregister(String sessionId);

    long count();
}
