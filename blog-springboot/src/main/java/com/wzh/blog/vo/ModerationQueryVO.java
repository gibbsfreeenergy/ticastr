package com.wzh.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ModerationQueryVO extends SearchQueryVO {
    private Integer isReview;
}
