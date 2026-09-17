package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.dto.ArchiveDTO;
import com.wzh.blog.dto.ArticleBackDTO;
import com.wzh.blog.dto.ArticleDTO;
import com.wzh.blog.dto.ArticleHomeDTO;
import com.wzh.blog.dto.ArticleRecommendDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.vo.ArticleQueryVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章数据访问。
 */
@Repository
public interface ArticleDao extends BaseMapper<Article> {

    List<ArticleHomeDTO> listPublicArticlesAfter(@Param("cursorTime") LocalDateTime cursorTime,
                                                 @Param("cursorId") Integer cursorId,
                                                 @Param("limit") int limit);

    List<ArchiveDTO> listPublicArchivesAfter(@Param("cursorTime") LocalDateTime cursorTime,
                                             @Param("cursorId") Integer cursorId,
                                             @Param("limit") int limit);

    ArticleDTO getArticleById(@Param("articleId") Integer articleId);

    Article selectForUpdate(@Param("articleId") Integer articleId);

    List<ArticleBackDTO> listArticleBacks(@Param("current") Long current,
                                          @Param("size") Long size,
                                          @Param("condition") ArticleQueryVO condition);

    Integer countArticleBacks(@Param("condition") ArticleQueryVO condition);

    List<ArticleRecommendDTO> listRecommendArticles(@Param("articleId") Integer articleId);

    List<Article> listPublishedArticlesAfter(@Param("afterId") Integer afterId,
                                             @Param("limit") int limit);
}
