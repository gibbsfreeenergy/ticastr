package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Metadata returned by article list and editor metadata endpoints. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleMetadataDTO {

    private Integer id;
    private String articleCover;
    private String articleTitle;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer categoryId;
    private String categoryName;
    private List<TagDTO> tagDTOList;
    private List<String> tagNameList;
    private Integer type;
    private String originalUrl;
    private Integer isTop;
    private Integer isDelete;
    private Integer status;
    private Integer contentVersion;
    private String contentUrl;
}
