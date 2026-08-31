package com.wzh.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CommentQueryVO extends SearchQueryVO {
    private Integer type;
    private Integer isReview;
}
