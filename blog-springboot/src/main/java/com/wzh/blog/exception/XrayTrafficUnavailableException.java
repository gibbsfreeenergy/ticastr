package com.wzh.blog.exception;

/** Raised when the remote read-only traffic data source cannot be reached. */
public class XrayTrafficUnavailableException extends RuntimeException {

    public XrayTrafficUnavailableException(String message) {
        super(message);
    }

    public XrayTrafficUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
