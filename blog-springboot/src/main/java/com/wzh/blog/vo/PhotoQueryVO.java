package com.wzh.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PhotoQueryVO extends PageQueryVO {
    private Integer albumId;
    private Integer isDelete;
}
