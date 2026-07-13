package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SearchQueryVO extends PageQueryVO {

    @Schema(description = "Search keywords")
    private String keywords;
}
