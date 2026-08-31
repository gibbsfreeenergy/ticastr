package com.wzh.blog.content;

import com.wzh.blog.dao.ContentAssetDao;
import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.entity.Article;
import org.springframework.stereotype.Repository;

/** Read-side repository for active article content pointers. */
@Repository
public class ContentAssetStore {

    private final ContentAssetDao contentAssetDao;
    private final ArticleDao articleDao;

    public ContentAssetStore(ContentAssetDao contentAssetDao, ArticleDao articleDao) {
        this.contentAssetDao = contentAssetDao;
        this.articleDao = articleDao;
    }

    public ContentAsset findActive(Integer articleId) {
        return contentAssetDao.selectActiveByArticleId(articleId);
    }

    public ContentAsset findActivePublic(Integer articleId) {
        Article article = articleDao.selectById(articleId);
        if (article == null || !Integer.valueOf(0).equals(article.getIsDelete())
                || !Integer.valueOf(1).equals(article.getStatus())) {
            return null;
        }
        return findActive(articleId);
    }

    public ContentAsset findVersion(Integer articleId, Integer version) {
        return contentAssetDao.selectByArticleIdAndVersion(articleId, version);
    }
}
