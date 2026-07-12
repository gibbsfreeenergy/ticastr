package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页对象
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "分页对象")
public class PageResult<T> {

    /**
     * 分页列表
     */
    @Schema(description = "分页列表")
    private List<T> recordList;

    /**
     * 总数
     */
    @Schema(description = "总数")
    private Long count;

    public PageResult(List<T> recordList, Integer count) {
        this(recordList, count == null ? null : count.longValue());
    }

}
