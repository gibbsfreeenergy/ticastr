package com.wzh.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台文章筛选条件。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ArticleQueryVO extends SearchQueryVO {

    private Integer type;
    private Integer status;
    private Integer isDelete;
}
