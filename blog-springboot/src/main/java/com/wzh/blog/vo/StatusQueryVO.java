package com.wzh.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class StatusQueryVO extends PageQueryVO {
    private Integer status;
}
