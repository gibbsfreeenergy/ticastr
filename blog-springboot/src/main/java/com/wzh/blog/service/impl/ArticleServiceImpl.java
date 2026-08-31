package com.wzh.blog.service.impl;

import com.wzh.blog.web.PageQuery;
import com.wzh.blog.web.CursorCodec;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzh.blog.dao.*;
import com.wzh.blog.dto.*;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.ArticleTag;
import com.wzh.blog.entity.Category;
import com.wzh.blog.entity.Tag;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.service.ArticleService;
import com.wzh.blog.service.ArticleTaxonomyService;
import com.wzh.blog.service.EngagementService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.service.ArticleTagService;
import com.wzh.blog.service.TagService;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.content.ContentAssetStore;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.search.ArticleSearchApplicationService;
import com.wzh.blog.strategy.context.SearchStrategyContext;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.util.HTMLUtils;
import com.wzh.blog.vo.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.ARTICLE_SET;
import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.enums.ArticleStatusEnum.DRAFT;
import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;


/**
 * 文章服务
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Service
@Log4j2
public class ArticleServiceImpl extends ServiceImpl<ArticleDao, Article> implements ArticleService {

    private final ArticleDao articleDao;
    private final CategoryDao categoryDao;
    private final TagDao tagDao;
    private final ArticleTagDao articleTagDao;
    private final SearchStrategyContext searchStrategyContext;
    private final ArticleTaxonomyService articleTaxonomyService;
    private final EngagementService engagementService;
    private final AssetLifecycleService assetLifecycleService;
    private final ContentAssetStore contentAssetStore;
    private final CurrentUser currentUser;
    private final ArticleSearchApplicationService articleSearchApplicationService;
    private final Executor taskExecutor;
    private final CursorCodec cursorCodec;

    public ArticleServiceImpl(ArticleDao articleDao,
                              CategoryDao categoryDao, TagDao tagDao, ArticleTagDao articleTagDao,
                              SearchStrategyContext searchStrategyContext,
                              ArticleTaxonomyService articleTaxonomyService,
                              EngagementService engagementService,
                              AssetLifecycleService assetLifecycleService,
                              ContentAssetStore contentAssetStore,
                              CurrentUser currentUser,
                              ArticleSearchApplicationService articleSearchApplicationService,
                              @Qualifier("blogTaskExecutor") Executor taskExecutor,
                              CursorCodec cursorCodec) {
        this.articleDao = articleDao;
        this.categoryDao = categoryDao;
        this.tagDao = tagDao;
        this.articleTagDao = articleTagDao;
        this.searchStrategyContext = searchStrategyContext;
        this.articleTaxonomyService = articleTaxonomyService;
        this.engagementService = engagementService;
        this.assetLifecycleService = assetLifecycleService;
        this.contentAssetStore = contentAssetStore;
        this.currentUser = currentUser;
        this.articleSearchApplicationService = articleSearchApplicationService;
        this.taskExecutor = taskExecutor;
        this.cursorCodec = cursorCodec;
    }




    @Override
    public PageResult<ArchiveDTO> listArchives(PageQuery pageQuery) {
        Page<Article> page = new Page<>(pageQuery.current(), pageQuery.size());
        // 获取分页数据
        Page<Article> articlePage = articleDao.selectPage(page, new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getArticleTitle, Article::getCreateTime)
                .orderByDesc(Article::getCreateTime)
                .eq(Article::getIsDelete, FALSE)
                .eq(Article::getStatus, PUBLIC.getStatus()));
        List<ArchiveDTO> archiveDTOList = BeanCopyUtils.copyList(articlePage.getRecords(), ArchiveDTO.class);
        return new PageResult<>(archiveDTOList, (int) articlePage.getTotal());
    }

    @Override
    public CursorPageResult<ArchiveDTO> listArchives(CursorPageQuery pageQuery) {
        String fingerprint = cursorCodec.fingerprint("archives");
        CursorCodec.Cursor cursor = pageQuery.cursor() == null
                ? null : cursorCodec.decode(pageQuery.cursor(), fingerprint);
        List<ArchiveDTO> rows = articleDao.listPublicArchivesAfter(
                cursor == null ? null : cursor.createTime(),
                cursor == null ? null : cursor.id(),
                pageQuery.size() + 1);
        boolean hasNext = rows.size() > pageQuery.size();
        List<ArchiveDTO> items = hasNext ? rows.subList(0, pageQuery.size()) : rows;
        String nextCursor = hasNext && !items.isEmpty()
                ? cursorCodec.encode(items.get(items.size() - 1).getCreateTime(),
                items.get(items.size() - 1).getId(), fingerprint)
                : null;
        return new CursorPageResult<>(items, nextCursor, hasNext);
    }



    @Override
    public PageResult<ArticleBackDTO> listArticleBacks(ArticleQueryVO condition, PageQuery pageQuery) {
        // 查询文章总量
        Integer count = articleDao.countArticleBacks(condition);
        if (count == 0) {
            return new PageResult<>();
        }
        // 查询后台文章
        List<ArticleBackDTO> articleBackDTOList = articleDao.listArticleBacks(pageQuery.offset(), pageQuery.size(), condition);
        // 查询文章点赞量和浏览量
        Map<Integer, ArticleEngagementCountDTO> engagementCounts = engagementService.articleCounts(
                articleBackDTOList.stream().map(ArticleBackDTO::getId).toList());
        // 封装点赞量和浏览量，计数来自 MySQL 事实表；单次批量查询避免 N+1。
        articleBackDTOList.forEach(item -> {
            ArticleEngagementCountDTO counts = engagementCounts.get(item.getId());
            if (counts != null) {
                item.setViewsCount(Math.toIntExact(Math.min(Integer.MAX_VALUE,
                        counts.getViewsCount() == null ? 0L : counts.getViewsCount())));
                item.setLikeCount(Math.toIntExact(Math.min(Integer.MAX_VALUE,
                        counts.getLikesCount() == null ? 0L : counts.getLikesCount())));
            } else {
                item.setViewsCount(0);
                item.setLikeCount(0);
            }
        });
        return new PageResult<>(articleBackDTOList, count);
    }



    @Override
    public List<ArticleHomeDTO> listArticles(PageQuery pageQuery) {
        return articleDao.listArticles(pageQuery.offset(), pageQuery.size());
    }

    @Override
    public CursorPageResult<ArticleHomeDTO> listArticles(CursorPageQuery pageQuery) {
        String fingerprint = cursorCodec.fingerprint("articles");
        CursorCodec.Cursor cursor = pageQuery.cursor() == null
                ? null : cursorCodec.decode(pageQuery.cursor(), fingerprint);
        List<ArticleHomeDTO> rows = articleDao.listPublicArticlesAfter(
                cursor == null ? null : cursor.createTime(),
                cursor == null ? null : cursor.id(),
                pageQuery.size() + 1);
        boolean hasNext = rows.size() > pageQuery.size();
        List<ArticleHomeDTO> items = hasNext ? rows.subList(0, pageQuery.size()) : rows;
        String nextCursor = hasNext && !items.isEmpty()
                ? cursorCodec.encode(items.get(items.size() - 1).getCreateTime(),
                items.get(items.size() - 1).getId(), fingerprint)
                : null;
        return new CursorPageResult<>(items, nextCursor, hasNext);
    }



    @Override
    public ArticlePreviewListDTO listArticlesByCondition(ArticleQueryVO condition, PageQuery pageQuery) {
        // 查询文章
        List<ArticlePreviewDTO> articlePreviewDTOList = articleDao.listArticlesByCondition(pageQuery.offset(), pageQuery.size(), condition);
        // 搜索条件对应名(标签或分类名)
        String name;
        if (Objects.nonNull(condition.getCategoryId())) {
            Category category = categoryDao.selectOne(new LambdaQueryWrapper<Category>()
                    .select(Category::getCategoryName)
                    .eq(Category::getId, condition.getCategoryId()));
            if (category == null) {
                throw new NotFoundException("分类不存在");
            }
            name = category.getCategoryName();
        } else if (Objects.nonNull(condition.getTagId())) {
            Tag tag = tagDao.selectOne(new LambdaQueryWrapper<Tag>()
                    .select(Tag::getTagName)
                    .eq(Tag::getId, condition.getTagId()));
            if (tag == null) {
                throw new NotFoundException("标签不存在");
            }
            name = tag.getTagName();
        } else {
            throw new BizException("分类或标签条件不能为空");
        }
        return ArticlePreviewListDTO.builder()
                .articlePreviewDTOList(articlePreviewDTOList)
                .name(name)
                .build();
    }



    @Override
    public ArticleDTO getArticleById(Integer articleId) {
        // Validate visibility before starting any related/recommendation query.
        ArticleDTO article = articleDao.getArticleById(articleId);
        if (Objects.isNull(article)) {
            throw new NotFoundException("文章不存在");
        }
        // 查询推荐文章
        CompletableFuture<List<ArticleRecommendDTO>> recommendArticleList = CompletableFuture
                .supplyAsync(() -> articleDao.listRecommendArticles(articleId), taskExecutor);
        // 查询最新文章
        CompletableFuture<List<ArticleRecommendDTO>> newestArticleList = CompletableFuture
                .supplyAsync(() -> {
                    List<Article> articleList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                            .select(Article::getId, Article::getArticleTitle, Article::getArticleCover, Article::getCreateTime)
                            .eq(Article::getIsDelete, FALSE)
                            .eq(Article::getStatus, PUBLIC.getStatus())
                            .orderByDesc(Article::getId)
                            .last("limit 5"));
                    return BeanCopyUtils.copyList(articleList, ArticleRecommendDTO.class);
                }, taskExecutor);
        if (article.getContentVersion() != null) {
            article.setContentUrl("/articles/" + articleId + "/content");
        }
        // 更新文章浏览量
        engagementService.recordArticleView(articleId);
        // 查询上一篇下一篇文章
        Article lastArticle = articleDao.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getArticleTitle, Article::getArticleCover)
                .eq(Article::getIsDelete, FALSE)
                .eq(Article::getStatus, PUBLIC.getStatus())
                .lt(Article::getId, articleId)
                .orderByDesc(Article::getId)
                .last("limit 1"));
        Article nextArticle = articleDao.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getArticleTitle, Article::getArticleCover)
                .eq(Article::getIsDelete, FALSE)
                .eq(Article::getStatus, PUBLIC.getStatus())
                .gt(Article::getId, articleId)
                .orderByAsc(Article::getId)
                .last("limit 1"));
        article.setLastArticle(BeanCopyUtils.copyObject(lastArticle, ArticlePaginationDTO.class));
        article.setNextArticle(BeanCopyUtils.copyObject(nextArticle, ArticlePaginationDTO.class));
        // 封装点赞量和浏览量
        ArticleEngagementCountDTO counts = engagementService.articleCounts(List.of(articleId)).get(articleId);
        article.setViewsCount(counts == null || counts.getViewsCount() == null
                ? 0 : Math.toIntExact(Math.min(Integer.MAX_VALUE, counts.getViewsCount())));
        article.setLikeCount(counts == null || counts.getLikesCount() == null
                ? 0 : Math.toIntExact(Math.min(Integer.MAX_VALUE, counts.getLikesCount())));
        // 封装文章信息
        try {
            CompletableFuture.allOf(recommendArticleList, newestArticleList).get(3, TimeUnit.SECONDS);
            article.setRecommendArticleList(recommendArticleList.join());
            article.setNewestArticleList(newestArticleList.join());
        } catch (Exception e) {
            log.warn("Unable to load article recommendations for article {}", articleId, e);
            recommendArticleList.cancel(true);
            newestArticleList.cancel(true);
        }
        return article;
    }




    @Override
    public void saveArticleLike(Integer articleId) {
        engagementService.toggleArticleLike(currentUser.id(), articleId);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public Integer saveOrUpdateArticle(ArticleVO articleVO) {
        String previousCover = null;
        if (articleVO.getId() != null) {
            Article existingArticle = articleDao.selectById(articleVO.getId());
            previousCover = existingArticle == null ? null : existingArticle.getArticleCover();
        }
        articleVO.setArticleTitle(HTMLUtils.sanitizePlainText(articleVO.getArticleTitle()));
        // 保存文章分类
        Category category = articleTaxonomyService.resolveCategory(
                articleVO.getCategoryName(), articleVO.getStatus());
        // 保存或修改文章
        Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        if (Objects.nonNull(category)) {
            article.setCategoryId(category.getId());
        }
        article.setUserId(currentUser.id());
        this.saveOrUpdate(article);
        articleVO.setId(article.getId());
        engagementService.ensureArticle(article.getId());
        // 保存文章标签
        articleTaxonomyService.replaceTags(article.getId(), articleVO.getTagNameList());
        articleSearchApplicationService.scheduleIndex(article.getId());
        if (previousCover != null && !previousCover.equals(article.getArticleCover())) {
            assetLifecycleService.deleteAfterCommit(List.of(previousCover));
        }
        return article.getId();
    }



    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        // 修改文章置顶状态
        Article article = Article.builder()
                .id(articleTopVO.getId())
                .isTop(articleTopVO.getIsTop())
                .build();
        articleDao.updateById(article);
    }



    @Override
    public void updateArticleDelete(DeleteVO deleteVO) {
        // 修改文章逻辑删除状态
        List<Article> articleList = deleteVO.getIdList().stream()
                .map(id -> Article.builder()
                        .id(id)
                        .isTop(FALSE)
                        .isDelete(deleteVO.getIsDelete())
                        .build())
                .collect(Collectors.toList());
        this.updateBatchById(articleList);
        articleList.forEach(article -> articleSearchApplicationService.scheduleIndex(article.getId()));
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void deleteArticles(List<Integer> articleIdList) {
        List<String> fileReferences = articleDao.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getArticleCover)
                        .in(Article::getId, articleIdList))
                .stream()
                .map(Article::getArticleCover)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        // 删除文章标签关联
        articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>()
                .in(ArticleTag::getArticleId, articleIdList));
        // 删除文章
        articleDao.deleteByIds(articleIdList);
        articleIdList.forEach(articleSearchApplicationService::scheduleIndex);
        assetLifecycleService.deleteAfterCommit(fileReferences);
    }



    @Override
    public List<ArticleSearchDTO> listArticlesBySearch(ArticleQueryVO condition, PageQuery pageQuery) {
        return searchStrategyContext.executeSearchStrategy(condition.getKeywords(), pageQuery);
    }

    @Override
    public CursorPageResult<ArticleSearchDTO> listArticlesBySearch(String keywords, CursorPageQuery pageQuery) {
        return searchStrategyContext.executeSearchStrategy(keywords, pageQuery);
    }



    @Override
    public ArticleVO getArticleBackById(Integer articleId) {
        // 查询文章信息
        Article article = articleDao.selectById(articleId);
        if (article == null) {
            throw new NotFoundException("文章不存在");
        }
        // 查询文章分类
        Category category = categoryDao.selectById(article.getCategoryId());
        String categoryName = null;
        if (Objects.nonNull(category)) {
            categoryName = category.getCategoryName();
        }
        // 查询文章标签
        List<String> tagNameList = tagDao.listTagNameByArticleId(articleId);
        // 封装数据
        ArticleVO articleVO = BeanCopyUtils.copyObject(article, ArticleVO.class);
        ContentAsset currentContent = contentAssetStore.findActive(articleId);
        if (currentContent != null) {
            articleVO.setContentVersion(currentContent.getVersion());
        }
        articleVO.setCategoryName(categoryName);
        articleVO.setTagNameList(tagNameList);
        return articleVO;
    }


}
