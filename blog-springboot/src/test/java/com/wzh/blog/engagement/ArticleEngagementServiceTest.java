package com.wzh.blog.engagement;

import com.wzh.blog.dao.ArticleEngagementDao;
import com.wzh.blog.dao.ArticleLikeDao;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.wzh.blog.constant.CommonConst.ARTICLE_SET;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ArticleEngagementServiceTest {

    private ArticleEngagementDao engagementDao;
    private ArticleLikeDao likeDao;
    private HttpSession session;
    private ArticleEngagementService service;

    @BeforeEach
    void setUp() {
        engagementDao = mock(ArticleEngagementDao.class);
        likeDao = mock(ArticleLikeDao.class);
        session = mock(HttpSession.class);
        when(engagementDao.ensureRow(7)).thenReturn(0);
        when(engagementDao.incrementLikes(7)).thenReturn(1);
        service = new ArticleEngagementService(engagementDao, likeDao, session);
    }

    @Test
    void duplicateLikeIsIdempotentAndIncrementsDatabaseCounterOnce() {
        when(likeDao.insertIgnore(42, 7)).thenReturn(1, 0);

        assertTrue(service.likeArticle(42, 7));
        assertFalse(service.likeArticle(42, 7));

        verify(likeDao, times(2)).insertIgnore(42, 7);
        verify(engagementDao, times(1)).incrementLikes(7);
    }

    @Test
    void viewUpdateUsesOneAtomicDaoIncrementPerSessionAndArticle() {
        AtomicReference<Set<Integer>> viewed = new AtomicReference<>(new HashSet<>());
        when(session.getAttribute(ARTICLE_SET)).thenAnswer(invocation -> viewed.get());
        doAnswer(invocation -> {
            viewed.set(invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(eq(ARTICLE_SET), anySet());
        when(engagementDao.incrementViews(7)).thenReturn(1);

        service.recordArticleView(7);
        service.recordArticleView(7);

        verify(engagementDao, times(1)).incrementViews(7);
        verify(session, times(1)).setAttribute(eq(ARTICLE_SET), anySet());
    }
}
