package com.wzh.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class UserQueryVO extends SearchQueryVO {
    private Integer loginType;
    private Integer type;
}
