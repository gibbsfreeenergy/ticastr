package com.wzh.blog.vo;

import lombok.Data;

@Data
public class ArticleQueryVO extends SearchQueryVO {
    private Integer categoryId;
    private Integer tagId;
    private Integer type;
    private Integer status;
    private Integer isDelete;
}
