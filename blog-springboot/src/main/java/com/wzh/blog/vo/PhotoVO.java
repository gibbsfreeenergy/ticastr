package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 照片
 *
 * @author yezhiqiu
 * @date 2021/08/05
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "照片")
public class PhotoVO {

    /**
     * 相册id
     */
    @NotNull(message = "相册id不能为空")
    @Schema(description = "相册id")
    private Integer albumId;

    /**
     * 照片url列表
     */
    @Schema(description = "照片列表")
    private List<String> photoUrlList;

    /**
     * 照片id列表
     */
    @Schema(description = "照片id列表")
    private List<Integer> photoIdList;

}
