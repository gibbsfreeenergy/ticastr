package com.wzh.blog.vo;

import lombok.Data;

@Data
public class ModerationQueryVO extends SearchQueryVO {
    private Integer isReview;
}
