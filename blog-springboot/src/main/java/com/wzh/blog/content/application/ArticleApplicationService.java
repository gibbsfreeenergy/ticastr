package com.wzh.blog.content.application;

import com.wzh.blog.dto.ArchiveDTO;
import com.wzh.blog.dto.ArticleBackDTO;
import com.wzh.blog.dto.ArticleDTO;
import com.wzh.blog.dto.ArticleHomeDTO;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.service.ArticleService;
import com.wzh.blog.vo.ArticleQueryVO;
import com.wzh.blog.vo.ArticleTopVO;
import com.wzh.blog.vo.ArticleVO;
import com.wzh.blog.vo.DeleteVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import com.wzh.blog.web.PageQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章应用服务。
 */
@Service
public class ArticleApplicationService implements ArticleUseCase {

    private final ArticleService articleService;

    public ArticleApplicationService(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Override
    public CursorPageResult<ArchiveDTO> listArchives(CursorPageQuery pageQuery) {
        return articleService.listArchives(pageQuery);
    }

    @Override
    public PageResult<ArticleBackDTO> listArticleBacks(ArticleQueryVO condition, PageQuery pageQuery) {
        return articleService.listArticleBacks(condition, pageQuery);
    }

    @Override
    public CursorPageResult<ArticleHomeDTO> listArticles(CursorPageQuery pageQuery) {
        return articleService.listArticles(pageQuery);
    }

    @Override
    public CursorPageResult<ArticleSearchDTO> listArticlesBySearch(String keywords, CursorPageQuery pageQuery) {
        return articleService.listArticlesBySearch(keywords, pageQuery);
    }

    @Override
    public ArticleVO getArticleBackById(Integer articleId) {
        return articleService.getArticleBackById(articleId);
    }

    @Override
    public ArticleDTO getArticleById(Integer articleId) {
        return articleService.getArticleById(articleId);
    }

    @Override
    public Integer saveOrUpdateArticle(ArticleVO articleVO) {
        return articleService.saveOrUpdateArticle(articleVO);
    }

    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        articleService.updateArticleTop(articleTopVO);
    }

    @Override
    public void updateArticleDelete(DeleteVO deleteVO) {
        articleService.updateArticleDelete(deleteVO);
    }

    @Override
    public void deleteArticles(List<Integer> articleIdList) {
        articleService.deleteArticles(articleIdList);
    }
}
