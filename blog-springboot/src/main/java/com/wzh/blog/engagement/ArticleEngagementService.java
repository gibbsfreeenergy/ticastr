package com.wzh.blog.engagement;

import com.wzh.blog.dao.ArticleEngagementDao;
import com.wzh.blog.dao.ArticleLikeDao;
import com.wzh.blog.dto.ArticleEngagementCountDTO;
import com.wzh.blog.exception.NotFoundException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.wzh.blog.constant.CommonConst.ARTICLE_SET;

/** MySQL-backed article engagement facts; Redis is intentionally not required. */
@Service
public class ArticleEngagementService {

    private final ArticleEngagementDao engagementDao;
    private final ArticleLikeDao likeDao;
    private final HttpSession session;

    public ArticleEngagementService(ArticleEngagementDao engagementDao,
                                    ArticleLikeDao likeDao,
                                    HttpSession session) {
        this.engagementDao = engagementDao;
        this.likeDao = likeDao;
        this.session = session;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean likeArticle(Integer userId, Integer articleId) {
        requireIds(userId, articleId);
        ensureArticle(articleId);
        if (likeDao.insertIgnore(userId, articleId) == 1) {
            if (engagementDao.incrementLikes(articleId) != 1) {
                throw new IllegalStateException("文章点赞计数更新失败");
            }
            return true;
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleArticleLike(Integer userId, Integer articleId) {
        requireIds(userId, articleId);
        ensureArticle(articleId);
        if (likeDao.deleteByUserAndArticle(userId, articleId) == 1) {
            engagementDao.decrementLikes(articleId);
            return false;
        }
        likeDao.insertIgnore(userId, articleId);
        engagementDao.incrementLikes(articleId);
        return true;
    }

    public void ensureArticle(Integer articleId) {
        if (articleId == null || engagementDao.ensureRow(articleId) < 0) {
            throw new NotFoundException("文章不存在");
        }
    }

    public void recordArticleView(Integer articleId) {
        if (articleId == null) {
            return;
        }
        Set<Integer> viewedArticles = viewedArticles();
        if (viewedArticles.add(articleId)) {
            session.setAttribute(ARTICLE_SET, viewedArticles);
            ensureArticle(articleId);
            if (engagementDao.incrementViews(articleId) != 1) {
                throw new IllegalStateException("文章浏览计数更新失败");
            }
        }
    }

    public Map<Integer, ArticleEngagementCountDTO> countsFor(Collection<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<ArticleEngagementCountDTO> rows = engagementDao.listCounts(articleIds);
        Map<Integer, ArticleEngagementCountDTO> result = new HashMap<>();
        for (ArticleEngagementCountDTO row : rows) {
            result.put(row.getArticleId(), row);
        }
        return result;
    }

    private Set<Integer> viewedArticles() {
        Object value = session.getAttribute(ARTICLE_SET);
        if (value instanceof Set<?> existing) {
            Set<Integer> result = new HashSet<>();
            for (Object item : existing) {
                if (item instanceof Integer integer) {
                    result.add(integer);
                }
            }
            return result;
        }
        return new HashSet<>();
    }

    private void requireIds(Integer userId, Integer articleId) {
        if (userId == null || articleId == null) {
            throw new IllegalArgumentException("用户和文章不能为空");
        }
    }
}
