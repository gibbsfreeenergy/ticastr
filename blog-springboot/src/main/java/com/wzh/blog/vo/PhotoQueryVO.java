package com.wzh.blog.vo;

import lombok.Data;

@Data
public class PhotoQueryVO extends PageQueryVO {
    private Integer albumId;
    private Integer isDelete;
}
