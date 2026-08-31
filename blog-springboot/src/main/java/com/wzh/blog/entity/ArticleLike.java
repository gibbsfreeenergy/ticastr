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
@TableName("tb_article_like")
public class ArticleLike {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Integer userId;
    private Integer articleId;
    private LocalDateTime createdAt;
}
