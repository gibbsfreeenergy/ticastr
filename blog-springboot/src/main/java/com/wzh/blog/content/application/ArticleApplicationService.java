package com.wzh.blog.content.application;

import com.wzh.blog.dto.*;
import com.wzh.blog.service.ArticleService;
import com.wzh.blog.vo.ArticleQueryVO;
import com.wzh.blog.vo.ArticleTopVO;
import com.wzh.blog.vo.ArticleVO;
import com.wzh.blog.vo.DeleteVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.web.PageQuery;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/** Compatibility application facade while the legacy technical-layer service is migrated. */
@Service
public class ArticleApplicationService implements ArticleUseCase {

    private final ArticleService legacyService;

    public ArticleApplicationService(ArticleService legacyService) {
        this.legacyService = legacyService;
    }

    @Override
    public PageResult<ArchiveDTO> listArchives(PageQuery pageQuery) {
        return legacyService.listArchives(pageQuery);
    }

    @Override
    public CursorPageResult<ArchiveDTO> listArchives(CursorPageQuery pageQuery) {
        return legacyService.listArchives(pageQuery);
    }

    @Override
    public PageResult<ArticleBackDTO> listArticleBacks(ArticleQueryVO condition, PageQuery pageQuery) {
        return legacyService.listArticleBacks(condition, pageQuery);
    }

    @Override
    public List<ArticleHomeDTO> listArticles(PageQuery pageQuery) {
        return legacyService.listArticles(pageQuery);
    }

    @Override
    public CursorPageResult<ArticleHomeDTO> listArticles(CursorPageQuery pageQuery) {
        return legacyService.listArticles(pageQuery);
    }

    @Override
    public ArticlePreviewListDTO listArticlesByCondition(ArticleQueryVO condition, PageQuery pageQuery) {
        return legacyService.listArticlesByCondition(condition, pageQuery);
    }

    @Override
    public List<ArticleSearchDTO> listArticlesBySearch(ArticleQueryVO condition, PageQuery pageQuery) {
        return legacyService.listArticlesBySearch(condition, pageQuery);
    }

    @Override
    public CursorPageResult<ArticleSearchDTO> listArticlesBySearch(String keywords, CursorPageQuery pageQuery) {
        return legacyService.listArticlesBySearch(keywords, pageQuery);
    }

    @Override
    public ArticleVO getArticleBackById(Integer articleId) {
        return legacyService.getArticleBackById(articleId);
    }

    @Override
    public ArticleDTO getArticleById(Integer articleId) {
        return legacyService.getArticleById(articleId);
    }

    @Override
    public void saveArticleLike(Integer articleId) {
        legacyService.saveArticleLike(articleId);
    }

    @Override
    public Integer saveOrUpdateArticle(ArticleVO articleVO) {
        return legacyService.saveOrUpdateArticle(articleVO);
    }

    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        legacyService.updateArticleTop(articleTopVO);
    }

    @Override
    public void updateArticleDelete(DeleteVO deleteVO) {
        legacyService.updateArticleDelete(deleteVO);
    }

    @Override
    public void deleteArticles(List<Integer> articleIdList) {
        legacyService.deleteArticles(articleIdList);
    }
}
