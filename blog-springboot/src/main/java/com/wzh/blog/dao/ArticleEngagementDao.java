package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.dto.ArticleEngagementCountDTO;
import com.wzh.blog.dto.ArticleRankDTO;
import com.wzh.blog.entity.ArticleEngagement;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ArticleEngagementDao extends BaseMapper<ArticleEngagement> {

    int ensureRow(@Param("articleId") Integer articleId);

    int incrementViews(@Param("articleId") Integer articleId);

    int incrementLikes(@Param("articleId") Integer articleId);

    int decrementLikes(@Param("articleId") Integer articleId);

    List<ArticleEngagementCountDTO> listCounts(@Param("articleIds") Collection<Integer> articleIds);

    List<ArticleRankDTO> listTopByViews(@Param("limit") int limit);
}
