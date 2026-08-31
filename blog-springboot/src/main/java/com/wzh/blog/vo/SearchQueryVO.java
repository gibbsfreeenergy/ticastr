package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SearchQueryVO extends PageQueryVO {

    @Schema(description = "Search keywords")
    private String keywords;
}
