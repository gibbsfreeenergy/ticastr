package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQueryVO {

    @Schema(description = "Page number")
    @Min(value = 1, message = "Page number must be greater than zero")
    private Long current;

    @Schema(description = "Page size")
    @Min(value = 1, message = "Page size must be greater than zero")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private Long size;
}
