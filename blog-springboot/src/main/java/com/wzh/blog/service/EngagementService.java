package com.wzh.blog.service;

import com.wzh.blog.util.CommonUtils;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.wzh.blog.constant.CommonConst.ARTICLE_SET;
import static com.wzh.blog.constant.RedisPrefixConst.*;

/** Owns the atomic write side of likes and article view counting. */
@Service
public class EngagementService {

    private final RedisService redisService;
    private final HttpSession session;

    public EngagementService(RedisService redisService, HttpSession session) {
        this.redisService = redisService;
        this.session = session;
    }

    public void toggleArticleLike(Integer userId, Integer articleId) {
        toggle(ARTICLE_USER_LIKE, ARTICLE_LIKE_COUNT, userId, articleId);
    }

    public void toggleCommentLike(Integer userId, Integer commentId) {
        toggle(COMMENT_USER_LIKE, COMMENT_LIKE_COUNT, userId, commentId);
    }

    public void toggleTalkLike(Integer userId, Integer talkId) {
        toggle(TALK_USER_LIKE, TALK_LIKE_COUNT, userId, talkId);
    }

    public void recordArticleView(Integer articleId) {
        Set<Integer> viewedArticles = CommonUtils.castSet(
                Optional.ofNullable(session.getAttribute(ARTICLE_SET)).orElseGet(HashSet::new), Integer.class);
        if (viewedArticles.add(articleId)) {
            session.setAttribute(ARTICLE_SET, viewedArticles);
            redisService.zIncr(ARTICLE_VIEWS_COUNT, articleId, 1D);
        }
    }

    private void toggle(String userSetPrefix, String countHashKey, Integer userId, Integer targetId) {
        redisService.toggleMemberAndCount(userSetPrefix + userId, targetId, countHashKey);
    }
}
