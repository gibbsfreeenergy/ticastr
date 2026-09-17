package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.content.ContentAssetStore;
import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dto.ArchiveDTO;
import com.wzh.blog.dto.ArticleBackDTO;
import com.wzh.blog.dto.ArticleDTO;
import com.wzh.blog.dto.ArticleHomeDTO;
import com.wzh.blog.dto.ArticlePaginationDTO;
import com.wzh.blog.dto.ArticleRecommendDTO;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.search.ArticleSearchApplicationService;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.ArticleService;
import com.wzh.blog.strategy.context.SearchStrategyContext;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.util.HTMLUtils;
import com.wzh.blog.vo.ArticleQueryVO;
import com.wzh.blog.vo.ArticleTopVO;
import com.wzh.blog.vo.ArticleVO;
import com.wzh.blog.vo.DeleteVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.web.CursorCodec;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import com.wzh.blog.web.PageQuery;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;

/**
 * 文章核心读写服务。
 */
@Service
@Log4j2
public class ArticleServiceImpl extends ServiceImpl<ArticleDao, Article> implements ArticleService {

    private final ArticleDao articleDao;
    private final SearchStrategyContext searchStrategyContext;
    private final AssetLifecycleService assetLifecycleService;
    private final ContentAssetStore contentAssetStore;
    private final CurrentUser currentUser;
    private final ArticleSearchApplicationService articleSearchApplicationService;
    private final Executor taskExecutor;
    private final CursorCodec cursorCodec;

    public ArticleServiceImpl(ArticleDao articleDao,
                              SearchStrategyContext searchStrategyContext,
                              AssetLifecycleService assetLifecycleService,
                              ContentAssetStore contentAssetStore,
                              CurrentUser currentUser,
                              ArticleSearchApplicationService articleSearchApplicationService,
                              @Qualifier("blogTaskExecutor") Executor taskExecutor,
                              CursorCodec cursorCodec) {
        this.articleDao = articleDao;
        this.searchStrategyContext = searchStrategyContext;
        this.assetLifecycleService = assetLifecycleService;
        this.contentAssetStore = contentAssetStore;
        this.currentUser = currentUser;
        this.articleSearchApplicationService = articleSearchApplicationService;
        this.taskExecutor = taskExecutor;
        this.cursorCodec = cursorCodec;
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
        Integer count = articleDao.countArticleBacks(condition);
        if (count == 0) {
            return new PageResult<>();
        }
        return new PageResult<>(
                articleDao.listArticleBacks(pageQuery.offset(), pageQuery.size(), condition),
                count);
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
    public ArticleDTO getArticleById(Integer articleId) {
        ArticleDTO article = articleDao.getArticleById(articleId);
        if (article == null) {
            throw new NotFoundException("文章不存在");
        }
        CompletableFuture<List<ArticleRecommendDTO>> recommendArticles = CompletableFuture.supplyAsync(
                () -> articleDao.listRecommendArticles(articleId), taskExecutor);
        CompletableFuture<List<ArticleRecommendDTO>> newestArticles = CompletableFuture.supplyAsync(
                () -> articleDao.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getId, Article::getArticleTitle,
                                Article::getArticleCover, Article::getCreateTime)
                        .eq(Article::getIsDelete, FALSE)
                        .eq(Article::getStatus, PUBLIC.getStatus())
                        .orderByDesc(Article::getId)
                        .last("limit 5"))
                        .stream()
                        .map(item -> BeanCopyUtils.copyObject(item, ArticleRecommendDTO.class))
                        .toList(),
                taskExecutor);
        if (article.getContentVersion() != null) {
            article.setContentUrl("/articles/" + articleId + "/content");
        }
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
        try {
            CompletableFuture.allOf(recommendArticles, newestArticles).get(3, TimeUnit.SECONDS);
            article.setRecommendArticleList(recommendArticles.join());
            article.setNewestArticleList(newestArticles.join());
        } catch (Exception exception) {
            log.warn("Unable to load article recommendations for article {}", articleId, exception);
            recommendArticles.cancel(true);
            newestArticles.cancel(true);
        }
        return article;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer saveOrUpdateArticle(ArticleVO articleVO) {
        String previousCover = null;
        if (articleVO.getId() != null) {
            Article existingArticle = articleDao.selectById(articleVO.getId());
            previousCover = existingArticle == null ? null : existingArticle.getArticleCover();
        }
        articleVO.setArticleTitle(HTMLUtils.sanitizePlainText(articleVO.getArticleTitle()));
        Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        article.setUserId(currentUser.id());
        this.saveOrUpdate(article);
        articleVO.setId(article.getId());
        articleSearchApplicationService.scheduleIndex(article.getId());
        if (previousCover != null && !previousCover.equals(article.getArticleCover())) {
            assetLifecycleService.deleteAfterCommit(List.of(previousCover));
        }
        return article.getId();
    }

    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        Article article = Article.builder()
                .id(articleTopVO.getId())
                .isTop(articleTopVO.getIsTop())
                .build();
        articleDao.updateById(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleDelete(DeleteVO deleteVO) {
        List<Article> articles = deleteVO.getIdList().stream()
                .map(id -> Article.builder()
                        .id(id)
                        .isTop(FALSE)
                        .isDelete(deleteVO.getIsDelete())
                        .build())
                .collect(Collectors.toList());
        this.updateBatchById(articles);
        articles.forEach(article -> articleSearchApplicationService.scheduleIndex(article.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticles(List<Integer> articleIdList) {
        List<String> fileReferences = articleDao.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getArticleCover)
                        .in(Article::getId, articleIdList))
                .stream()
                .map(Article::getArticleCover)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        articleDao.deleteByIds(articleIdList);
        articleIdList.forEach(articleSearchApplicationService::scheduleIndex);
        assetLifecycleService.deleteAfterCommit(fileReferences);
    }

    @Override
    public CursorPageResult<ArticleSearchDTO> listArticlesBySearch(String keywords, CursorPageQuery pageQuery) {
        return searchStrategyContext.executeSearchStrategy(keywords, pageQuery);
    }

    @Override
    public ArticleVO getArticleBackById(Integer articleId) {
        Article article = articleDao.selectById(articleId);
        if (article == null) {
            throw new NotFoundException("文章不存在");
        }
        ArticleVO articleVO = BeanCopyUtils.copyObject(article, ArticleVO.class);
        ContentAsset currentContent = contentAssetStore.findActive(articleId);
        if (currentContent != null) {
            articleVO.setContentVersion(currentContent.getVersion());
        }
        return articleVO;
    }
}
