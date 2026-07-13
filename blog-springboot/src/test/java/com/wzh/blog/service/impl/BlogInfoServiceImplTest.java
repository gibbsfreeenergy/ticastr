package com.wzh.blog.service.impl;

import com.wzh.blog.dao.AboutDao;
import com.wzh.blog.entity.About;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.vo.BlogInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.wzh.blog.constant.CommonConst.DEFAULT_CONFIG_ID;
import static com.wzh.blog.constant.RedisPrefixConst.ABOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogInfoServiceImplTest {

    @InjectMocks
    private BlogInfoServiceImpl blogInfoService;

    @Mock
    private AboutDao aboutDao;
    @Mock
    private RedisService redisService;

    @Test
    void readsAboutFromDatabaseWhenCacheIsEmpty() {
        when(aboutDao.selectById(DEFAULT_CONFIG_ID)).thenReturn(About.builder()
                .id(DEFAULT_CONFIG_ID)
                .content("Persistent content")
                .build());

        String content = blogInfoService.getAbout();

        assertEquals("Persistent content", content);
        verify(redisService).set(ABOUT, "Persistent content");
    }

    @Test
    void persistsAboutBeforeRefreshingCache() {
        BlogInfoVO about = BlogInfoVO.builder().aboutContent("Updated content").build();
        when(aboutDao.updateById(any(About.class))).thenReturn(1);

        blogInfoService.updateAbout(about);

        verify(aboutDao).updateById(About.builder()
                .id(DEFAULT_CONFIG_ID)
                .content("Updated content")
                .build());
        verify(redisService).set(ABOUT, "Updated content");
    }
}
