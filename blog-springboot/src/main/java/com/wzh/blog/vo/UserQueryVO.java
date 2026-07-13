package com.wzh.blog.vo;

import lombok.Data;

@Data
public class UserQueryVO extends SearchQueryVO {
    private Integer loginType;
    private Integer type;
}
