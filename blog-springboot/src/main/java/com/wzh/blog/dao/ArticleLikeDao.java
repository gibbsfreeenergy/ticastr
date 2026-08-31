package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.entity.ArticleLike;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleLikeDao extends BaseMapper<ArticleLike> {

    int insertIgnore(@Param("userId") Integer userId, @Param("articleId") Integer articleId);

    int deleteByUserAndArticle(@Param("userId") Integer userId, @Param("articleId") Integer articleId);

    boolean existsByUserAndArticle(@Param("userId") Integer userId, @Param("articleId") Integer articleId);
}
