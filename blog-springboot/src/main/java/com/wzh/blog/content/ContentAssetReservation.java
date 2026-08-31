package com.wzh.blog.content;

/** Result of reserving a new immutable content version. */
public record ContentAssetReservation(ContentAsset asset, ContentAsset previousActive) {
}
