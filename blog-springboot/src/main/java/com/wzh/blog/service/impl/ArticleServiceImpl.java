package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
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
import com.wzh.blog.service.RedisService;
import com.wzh.blog.service.TagService;
import com.wzh.blog.strategy.context.SearchStrategyContext;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.util.CommonUtils;
import com.wzh.blog.util.HTMLUtils;
import com.wzh.blog.util.PageUtils;
import com.wzh.blog.util.UserUtils;
import com.wzh.blog.vo.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.ARTICLE_SET;
import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.constant.RedisPrefixConst.*;
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
    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private ArticleTagDao articleTagDao;
    @Autowired
    private SearchStrategyContext searchStrategyContext;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ArticleTaxonomyService articleTaxonomyService;
    @Autowired
    private EngagementService engagementService;
    @Autowired
    @Qualifier("blogTaskExecutor")
    private Executor taskExecutor;




    @Override
    public PageResult<ArchiveDTO> listArchives() {
        Page<Article> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
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
    public PageResult<ArticleBackDTO> listArticleBacks(ConditionVO condition) {
        // 查询文章总量
        Integer count = articleDao.countArticleBacks(condition);
        if (count == 0) {
            return new PageResult<>();
        }
        // 查询后台文章
        List<ArticleBackDTO> articleBackDTOList = articleDao.listArticleBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        // 查询文章点赞量和浏览量
        Map<Object, Double> viewsCountMap = redisService.zAllScore(ARTICLE_VIEWS_COUNT);
        Map<String, Object> likeCountMap = redisService.hGetAll(ARTICLE_LIKE_COUNT);
        // 封装点赞量和浏览量
        articleBackDTOList.forEach(item -> {
            Double viewsCount = viewsCountMap.get(item.getId());
            if (Objects.nonNull(viewsCount)) {
                item.setViewsCount(viewsCount.intValue());
            }
            item.setLikeCount((Integer) likeCountMap.get(item.getId().toString()));
        });
        return new PageResult<>(articleBackDTOList, count);
    }



    @Override
    public List<ArticleHomeDTO> listArticles() {
        return articleDao.listArticles(PageUtils.getLimitCurrent(), PageUtils.getSize());
    }



    @Override
    public ArticlePreviewListDTO listArticlesByCondition(ConditionVO condition) {
        // 查询文章
        List<ArticlePreviewDTO> articlePreviewDTOList = articleDao.listArticlesByCondition(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
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
        // 查询id对应文章
        ArticleDTO article = articleDao.getArticleById(articleId);
        if (Objects.isNull(article)) {
            throw new NotFoundException("文章不存在");
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
        Double score = redisService.zScore(ARTICLE_VIEWS_COUNT, articleId);
        if (Objects.nonNull(score)) {
            article.setViewsCount(score.intValue());
        }
        article.setLikeCount((Integer) redisService.hGet(ARTICLE_LIKE_COUNT, articleId.toString()));
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
        engagementService.toggleArticleLike(UserUtils.getLoginUser().getUserInfoId(), articleId);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void saveOrUpdateArticle(ArticleVO articleVO) {
        articleVO.setArticleTitle(HTMLUtils.sanitizePlainText(articleVO.getArticleTitle()));
        articleVO.setArticleContent(HTMLUtils.sanitizeRichText(articleVO.getArticleContent()));
        // 保存文章分类
        Category category = articleTaxonomyService.resolveCategory(
                articleVO.getCategoryName(), articleVO.getStatus());
        // 保存或修改文章
        Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        if (Objects.nonNull(category)) {
            article.setCategoryId(category.getId());
        }
        article.setUserId(UserUtils.getLoginUser().getUserInfoId());
        this.saveOrUpdate(article);
        // 保存文章标签
        articleTaxonomyService.replaceTags(article.getId(), articleVO.getTagNameList());
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
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void deleteArticles(List<Integer> articleIdList) {
        // 删除文章标签关联
        articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>()
                .in(ArticleTag::getArticleId, articleIdList));
        // 删除文章
        articleDao.deleteByIds(articleIdList);
    }



    @Override
    public List<ArticleSearchDTO> listArticlesBySearch(ConditionVO condition) {
        return searchStrategyContext.executeSearchStrategy(condition.getKeywords());
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
        articleVO.setCategoryName(categoryName);
        articleVO.setTagNameList(tagNameList);
        return articleVO;
    }


}
