package com.wzh.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wzh.blog.dto.ArchiveDTO;
import com.wzh.blog.dto.ArticleBackDTO;
import com.wzh.blog.dto.ArticleDTO;
import com.wzh.blog.dto.ArticleHomeDTO;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.vo.ArticleQueryVO;
import com.wzh.blog.vo.ArticleTopVO;
import com.wzh.blog.vo.ArticleVO;
import com.wzh.blog.vo.DeleteVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import com.wzh.blog.web.PageQuery;

import java.util.List;

/**
 * 文章服务。
 */
public interface ArticleService extends IService<Article> {

    CursorPageResult<ArchiveDTO> listArchives(CursorPageQuery pageQuery);

    PageResult<ArticleBackDTO> listArticleBacks(ArticleQueryVO condition, PageQuery pageQuery);

    CursorPageResult<ArticleHomeDTO> listArticles(CursorPageQuery pageQuery);

    CursorPageResult<ArticleSearchDTO> listArticlesBySearch(String keywords, CursorPageQuery pageQuery);

    ArticleVO getArticleBackById(Integer articleId);

    ArticleDTO getArticleById(Integer articleId);

    Integer saveOrUpdateArticle(ArticleVO articleVO);

    void updateArticleTop(ArticleTopVO articleTopVO);

    void updateArticleDelete(DeleteVO deleteVO);

    void deleteArticles(List<Integer> articleIdList);
}
