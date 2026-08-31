package com.wzh.blog.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.dao.*;
import com.wzh.blog.dto.*;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.About;
import com.wzh.blog.entity.WebsiteConfig;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.service.BlogInfoService;
import com.wzh.blog.service.PageService;
import com.wzh.blog.infrastructure.cache.CacheKeyFactory;
import com.wzh.blog.infrastructure.cache.CacheStore;
import com.wzh.blog.service.UniqueViewService;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.util.IpUtils;
import com.wzh.blog.vo.BlogInfoVO;
import com.wzh.blog.vo.PageVO;
import com.wzh.blog.vo.WebsiteConfigVO;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

import static com.wzh.blog.constant.CommonConst.*;
import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;

/**
 * 博客信息服务
 *
 * @author xiaojie
 * @date 2021/08/10
 * @since 2020-05-18
 */
@Service
public class BlogInfoServiceImpl implements BlogInfoService {
    private final UserInfoDao userInfoDao;
    private final ArticleDao articleDao;
    private final CategoryDao categoryDao;
    private final TagDao tagDao;
    private final MessageDao messageDao;
    private final UniqueViewService uniqueViewService;
    private final ArticleEngagementDao articleEngagementDao;
    private final CacheStore cacheStore;
    private final CacheKeyFactory cacheKeyFactory;
    private final WebsiteConfigDao websiteConfigDao;
    private final HttpServletRequest request;
    private final PageService pageService;
    private final AboutDao aboutDao;

    public BlogInfoServiceImpl(UserInfoDao userInfoDao,
                               ArticleDao articleDao,
                               CategoryDao categoryDao,
                               TagDao tagDao,
                               MessageDao messageDao,
                               UniqueViewService uniqueViewService,
                               ArticleEngagementDao articleEngagementDao,
                               CacheStore cacheStore,
                               WebsiteConfigDao websiteConfigDao,
                               HttpServletRequest request,
                               PageService pageService,
                               AboutDao aboutDao) {
        this.userInfoDao = userInfoDao;
        this.articleDao = articleDao;
        this.categoryDao = categoryDao;
        this.tagDao = tagDao;
        this.messageDao = messageDao;
        this.uniqueViewService = uniqueViewService;
        this.articleEngagementDao = articleEngagementDao;
        this.cacheStore = cacheStore;
        this.cacheKeyFactory = new CacheKeyFactory();
        this.websiteConfigDao = websiteConfigDao;
        this.request = request;
        this.pageService = pageService;
        this.aboutDao = aboutDao;
    }

    @Override
    public BlogHomeInfoDTO getBlogHomeInfo() {
        // 查询文章数量
        Long articleCount = articleDao.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, PUBLIC.getStatus())
                .eq(Article::getIsDelete, FALSE));
        // 查询分类数量
        Long categoryCount = categoryDao.selectCount(null);
        // 查询标签数量
        Long tagCount = tagDao.selectCount(null);
        // 查询访问量
        String viewsCount = String.valueOf(uniqueViewService.totalViews());
        // 查询网站配置
        WebsiteConfigVO websiteConfig = this.getWebsiteConfig();
        // 查询页面图片
        List<PageVO> pageVOList = pageService.listPages();
        // 封装数据
        return BlogHomeInfoDTO.builder()
                .articleCount(articleCount)
                .categoryCount(categoryCount)
                .tagCount(tagCount)
                .viewsCount(viewsCount)
                .websiteConfig(websiteConfig)
                .pageList(pageVOList)
                .build();
    }

    @Override
    public BlogBackInfoDTO getBlogBackInfo() {
        // 查询访问量
        Integer viewsCount = Math.toIntExact(Math.min(Integer.MAX_VALUE, uniqueViewService.totalViews()));
        // 查询留言量
        Long messageCount = messageDao.selectCount(null);
        // 查询用户量
        Long userCount = userInfoDao.selectCount(null);
        // 查询文章量
        Long articleCount = articleDao.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getIsDelete, FALSE));
        // 查询一周用户量
        List<UniqueViewDTO> uniqueViewList = uniqueViewService.listUniqueViews();
        // 查询文章统计
        List<ArticleStatisticsDTO> articleStatisticsList = articleDao.listArticleStatistics();
        // 查询分类数据
        List<CategoryDTO> categoryDTOList = categoryDao.listCategoryDTO();
        // 查询标签数据
        List<TagDTO> tagDTOList = BeanCopyUtils.copyList(tagDao.selectList(null), TagDTO.class);
        // 文章浏览量来自 MySQL 事实表，Redis 关闭时仍保持一致。
        List<ArticleRankDTO> articleRankDTOList = articleEngagementDao.listTopByViews(5);
        BlogBackInfoDTO blogBackInfoDTO = BlogBackInfoDTO.builder()
                .articleStatisticsList(articleStatisticsList)
                .tagDTOList(tagDTOList)
                .viewsCount(viewsCount)
                .messageCount(messageCount)
                .userCount(userCount)
                .articleCount(articleCount)
                .categoryDTOList(categoryDTOList)
                .uniqueViewDTOList(uniqueViewList)
                .build();
        if (CollectionUtils.isNotEmpty(articleRankDTOList)) {
            blogBackInfoDTO.setArticleRankDTOList(articleRankDTOList);
        }
        return blogBackInfoDTO;
    }

    @Override
    public void updateWebsiteConfig(WebsiteConfigVO websiteConfigVO) {
        // 修改网站配置
        WebsiteConfig websiteConfig = WebsiteConfig.builder()
                .id(1)
                .config(JSON.toJSONString(websiteConfigVO))
                .build();
        websiteConfigDao.updateById(websiteConfig);
        // 删除缓存
        cacheStore.evict(cacheKeyFactory.websiteConfig());
    }

    @Override
    public WebsiteConfigVO getWebsiteConfig() {
        WebsiteConfigVO websiteConfigVO;
        // 获取缓存数据
        Object websiteConfig = cacheStore.get(cacheKeyFactory.websiteConfig());
        if (websiteConfig instanceof WebsiteConfigVO cached) {
            websiteConfigVO = cached;
        } else if (websiteConfig instanceof String cached) {
            websiteConfigVO = JSON.parseObject(cached, WebsiteConfigVO.class);
        } else {
            // 从数据库中加载
            WebsiteConfig storedConfig = websiteConfigDao.selectById(DEFAULT_CONFIG_ID);
            if (storedConfig == null || storedConfig.getConfig() == null) {
                throw new NotFoundException("网站配置不存在");
            }
            String config = storedConfig.getConfig();
            websiteConfigVO = JSON.parseObject(config, WebsiteConfigVO.class);
            cacheStore.put(cacheKeyFactory.websiteConfig(), websiteConfigVO, java.time.Duration.ofMinutes(5));
        }
        return websiteConfigVO;
    }

    @Override
    public String getAbout() {
        Object value = cacheStore.get(cacheKeyFactory.about());
        if (value instanceof String content) {
            return content;
        }
        About about = aboutDao.selectById(DEFAULT_CONFIG_ID);
        String content = about == null ? "" : about.getContent();
        cacheStore.put(cacheKeyFactory.about(), content, java.time.Duration.ofMinutes(5));
        return content;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAbout(BlogInfoVO blogInfoVO) {
        persistAbout(blogInfoVO.getAboutContent());
        cacheStore.put(cacheKeyFactory.about(), blogInfoVO.getAboutContent(), java.time.Duration.ofMinutes(5));
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

    @Override
    public void report() {
        // 获取ip
        String ipAddress = IpUtils.getIpAddress(request);
        // 获取访问设备
        UserAgent userAgent = IpUtils.getUserAgent(request);
        Browser browser = userAgent.getBrowser();
        OperatingSystem operatingSystem = userAgent.getOperatingSystem();
        // 生成唯一用户标识
        String uuid = ipAddress + browser.getName() + operatingSystem.getName();
        String md5 = DigestUtils.md5DigestAsHex(uuid.getBytes());
        String ipSource = IpUtils.getIpSource(ipAddress);
        String area = StringUtils.isNotBlank(ipSource)
                ? ipSource.substring(0, Math.min(2, ipSource.length()))
                    .replaceAll(PROVINCE, "")
                    .replaceAll(CITY, "")
                : UNKNOWN;
        uniqueViewService.recordVisitor(md5, area);
    }

}
