package com.wzh.blog.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.AboutDao;
import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dao.WebsiteConfigDao;
import com.wzh.blog.dto.BlogBackInfoDTO;
import com.wzh.blog.dto.BlogHomeInfoDTO;
import com.wzh.blog.entity.About;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.WebsiteConfig;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.infrastructure.cache.CacheKeyFactory;
import com.wzh.blog.infrastructure.cache.CacheStore;
import com.wzh.blog.service.BlogInfoService;
import com.wzh.blog.service.PageService;
import com.wzh.blog.vo.BlogInfoVO;
import com.wzh.blog.vo.PageVO;
import com.wzh.blog.vo.WebsiteConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static com.wzh.blog.constant.CommonConst.DEFAULT_CONFIG_ID;
import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;

/**
 * 首页、关于页和网站基础配置服务。
 */
@Service
public class BlogInfoServiceImpl implements BlogInfoService {

    private final ArticleDao articleDao;
    private final CacheStore cacheStore;
    private final CacheKeyFactory cacheKeyFactory;
    private final WebsiteConfigDao websiteConfigDao;
    private final PageService pageService;
    private final AboutDao aboutDao;

    public BlogInfoServiceImpl(ArticleDao articleDao,
                               CacheStore cacheStore,
                               WebsiteConfigDao websiteConfigDao,
                               PageService pageService,
                               AboutDao aboutDao) {
        this.articleDao = articleDao;
        this.cacheStore = cacheStore;
        this.cacheKeyFactory = new CacheKeyFactory();
        this.websiteConfigDao = websiteConfigDao;
        this.pageService = pageService;
        this.aboutDao = aboutDao;
    }

    @Override
    public BlogHomeInfoDTO getBlogHomeInfo() {
        Long articleCount = articleDao.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, PUBLIC.getStatus())
                .eq(Article::getIsDelete, FALSE));
        List<PageVO> pageList = pageService.listPages();
        return BlogHomeInfoDTO.builder()
                .articleCount(articleCount)
                .websiteConfig(getWebsiteConfig())
                .pageList(pageList)
                .build();
    }

    @Override
    public BlogBackInfoDTO getBlogBackInfo() {
        Long articleCount = articleDao.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getIsDelete, FALSE));
        return BlogBackInfoDTO.builder()
                .articleCount(articleCount)
                .build();
    }

    @Override
    public void updateWebsiteConfig(WebsiteConfigVO websiteConfigVO) {
        WebsiteConfig websiteConfig = WebsiteConfig.builder()
                .id(DEFAULT_CONFIG_ID)
                .config(JSON.toJSONString(websiteConfigVO))
                .build();
        websiteConfigDao.updateById(websiteConfig);
        cacheStore.evict(cacheKeyFactory.websiteConfig());
    }

    @Override
    public WebsiteConfigVO getWebsiteConfig() {
        Object cached = cacheStore.get(cacheKeyFactory.websiteConfig());
        if (cached instanceof WebsiteConfigVO websiteConfigVO) {
            return websiteConfigVO;
        }
        if (cached instanceof String config) {
            return JSON.parseObject(config, WebsiteConfigVO.class);
        }
        WebsiteConfig storedConfig = websiteConfigDao.selectById(DEFAULT_CONFIG_ID);
        if (storedConfig == null || storedConfig.getConfig() == null) {
            throw new NotFoundException("网站配置不存在");
        }
        WebsiteConfigVO websiteConfigVO = JSON.parseObject(storedConfig.getConfig(), WebsiteConfigVO.class);
        cacheStore.put(cacheKeyFactory.websiteConfig(), websiteConfigVO, Duration.ofMinutes(5));
        return websiteConfigVO;
    }

    @Override
    public String getAbout() {
        Object cached = cacheStore.get(cacheKeyFactory.about());
        if (cached instanceof String content) {
            return content;
        }
        About about = aboutDao.selectById(DEFAULT_CONFIG_ID);
        String content = about == null ? "" : about.getContent();
        cacheStore.put(cacheKeyFactory.about(), content, Duration.ofMinutes(5));
        return content;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAbout(BlogInfoVO blogInfoVO) {
        persistAbout(blogInfoVO.getAboutContent());
        cacheStore.put(cacheKeyFactory.about(), blogInfoVO.getAboutContent(), Duration.ofMinutes(5));
    }

    private void persistAbout(String content) {
        About about = About.builder()
                .id(DEFAULT_CONFIG_ID)
                .content(content)
                .build();
        if (aboutDao.updateById(about) == 0) {
            aboutDao.insert(about);
        }
    }
}
