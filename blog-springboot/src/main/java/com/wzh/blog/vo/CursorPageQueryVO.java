package com.wzh.blog.vo;

import com.wzh.blog.web.CursorPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** HTTP adapter for public cursor pagination. */
@Data
public class CursorPageQueryVO {

    @Schema(description = "Opaque continuation cursor")
    private String cursor;

    @Schema(description = "Page size")
    @Min(value = 1, message = "Page size must be greater than zero")
    @Max(value = 50, message = "Page size cannot exceed 50")
    private Integer size;

    public CursorPageQuery toCursorPageQuery() {
        return CursorPageQuery.of(cursor, size);
    }
}
