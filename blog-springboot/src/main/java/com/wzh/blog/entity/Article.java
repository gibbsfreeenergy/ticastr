package com.wzh.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Persisted metadata for a blog article. The Markdown body lives in content assets. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_article")
public class Article {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private String articleCover;
    private String articleTitle;
    private String contentAssetId;
    private Integer type;
    private String originalUrl;
    private Integer isTop;
    private Integer isDelete;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
