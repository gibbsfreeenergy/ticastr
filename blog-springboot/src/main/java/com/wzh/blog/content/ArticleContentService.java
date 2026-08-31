package com.wzh.blog.content;

import com.wzh.blog.media.StorageObject;
import com.wzh.blog.vo.ArticleContentRequest;
import com.wzh.blog.vo.ArticleContentResponse;
import com.wzh.blog.vo.ArticleContentVersionPage;

/** Application port for article Markdown versions. */
public interface ArticleContentService {

    ArticleContentResponse replace(Integer articleId, ArticleContentRequest request);

    ContentAsset currentAsset(Integer articleId);

    ContentAsset currentPublicAsset(Integer articleId);

    /** Returns no asset for a draft/deleted article without turning projection cleanup into an error. */
    default ContentAsset currentPublicAssetOrNull(Integer articleId) {
        try {
            return currentPublicAsset(articleId);
        } catch (com.wzh.blog.exception.NotFoundException exception) {
            return null;
        }
    }

    StorageObject open(Integer articleId);

    /** Opens the exact immutable asset already selected by the caller. */
    StorageObject open(ContentAsset asset);

    StorageObject openPublic(Integer articleId);

    ArticleContentVersionPage versions(Integer articleId, String cursor, Integer size);

    ArticleContentResponse restore(Integer articleId, Integer version, Integer expectedVersion);
}
