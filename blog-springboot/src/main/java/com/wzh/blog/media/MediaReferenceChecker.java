package com.wzh.blog.media;

/**
 * Checks whether a legacy URL/reference is still owned by a business record.
 * Keeping this decision behind a port prevents storage providers from needing
 * to know database table layouts.
 */
public interface MediaReferenceChecker {

    /**
     * @return true when the reference is still in use, or when the check
     * cannot be completed safely
     */
    boolean isReferenced(String fileReference);
}
