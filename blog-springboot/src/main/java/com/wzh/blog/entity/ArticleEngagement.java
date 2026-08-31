package com.wzh.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_article_engagement")
public class ArticleEngagement {
    @TableId(value = "article_id", type = IdType.INPUT)
    private Integer articleId;
    private Long viewsCount;
    private Long likesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
