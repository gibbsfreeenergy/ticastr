package com.wzh.blog.vo;

import lombok.Data;

@Data
public class CommentQueryVO extends SearchQueryVO {
    private Integer type;
    private Integer isReview;
}
