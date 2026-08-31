package com.wzh.blog.content.application;

import com.wzh.blog.dto.*;
import com.wzh.blog.vo.ArticleQueryVO;
import com.wzh.blog.vo.ArticleTopVO;
import com.wzh.blog.vo.ArticleVO;
import com.wzh.blog.vo.DeleteVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.web.PageQuery;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;

import java.util.List;

/** Public application boundary for article use cases. */
public interface ArticleUseCase {

    PageResult<ArchiveDTO> listArchives(PageQuery pageQuery);

    CursorPageResult<ArchiveDTO> listArchives(CursorPageQuery pageQuery);

    PageResult<ArticleBackDTO> listArticleBacks(ArticleQueryVO condition, PageQuery pageQuery);

    List<ArticleHomeDTO> listArticles(PageQuery pageQuery);

    CursorPageResult<ArticleHomeDTO> listArticles(CursorPageQuery pageQuery);

    ArticlePreviewListDTO listArticlesByCondition(ArticleQueryVO condition, PageQuery pageQuery);

    List<ArticleSearchDTO> listArticlesBySearch(ArticleQueryVO condition, PageQuery pageQuery);

    CursorPageResult<ArticleSearchDTO> listArticlesBySearch(String keywords, CursorPageQuery pageQuery);

    ArticleVO getArticleBackById(Integer articleId);

    ArticleDTO getArticleById(Integer articleId);

    void saveArticleLike(Integer articleId);

    Integer saveOrUpdateArticle(ArticleVO articleVO);

    void updateArticleTop(ArticleTopVO articleTopVO);

    void updateArticleDelete(DeleteVO deleteVO);

    void deleteArticles(List<Integer> articleIdList);
}
