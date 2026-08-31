package com.wzh.blog.service;

import com.wzh.blog.engagement.ArticleEngagementService;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.wzh.blog.dto.ArticleEngagementCountDTO;
import static com.wzh.blog.constant.RedisPrefixConst.*;

/** Owns the atomic write side of likes and article view counting. */
@Service
public class EngagementService {

    private final RedisService redisService;
    private final ArticleEngagementService articleEngagementService;

    public EngagementService(RedisService redisService,
                              ArticleEngagementService articleEngagementService) {
        this.redisService = redisService;
        this.articleEngagementService = articleEngagementService;
    }

    public void toggleArticleLike(Integer userId, Integer articleId) {
        articleEngagementService.toggleArticleLike(userId, articleId);
    }

    public void toggleCommentLike(Integer userId, Integer commentId) {
        toggle(COMMENT_USER_LIKE, COMMENT_LIKE_COUNT, userId, commentId);
    }

    public void toggleTalkLike(Integer userId, Integer talkId) {
        toggle(TALK_USER_LIKE, TALK_LIKE_COUNT, userId, talkId);
    }

    public void recordArticleView(Integer articleId) {
        articleEngagementService.recordArticleView(articleId);
    }

    public void ensureArticle(Integer articleId) {
        articleEngagementService.ensureArticle(articleId);
    }

    public Map<Integer, ArticleEngagementCountDTO> articleCounts(java.util.Collection<Integer> articleIds) {
        return articleEngagementService.countsFor(articleIds);
    }

    private void toggle(String userSetPrefix, String countHashKey, Integer userId, Integer targetId) {
        redisService.toggleMemberAndCount(userSetPrefix + userId, targetId, countHashKey);
    }
}
