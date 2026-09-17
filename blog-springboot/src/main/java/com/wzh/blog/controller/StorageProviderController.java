package com.wzh.blog.controller;

import com.wzh.blog.administration.StorageConfigAdminService;
import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.StorageConfigListResponse;
import com.wzh.blog.vo.StorageConfigRequest;
import com.wzh.blog.vo.StorageConfigSummaryVO;
import com.wzh.blog.vo.StorageUsageVO;
import com.wzh.blog.vo.StorageValidationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "对象存储配置")
@RestController
@RequestMapping("/admin/storage")
public class StorageProviderController {

    private final StorageConfigAdminService selectionService;
    private final CurrentUser currentUser;

    public StorageProviderController(StorageConfigAdminService selectionService,
                                     CurrentUser currentUser) {
        this.selectionService = selectionService;
        this.currentUser = currentUser;
    }

    @Operation(summary = "查看对象存储配置档案")
    @GetMapping("/configs")
    public Result<StorageConfigListResponse> configs() {
        return Result.ok(selectionService.list());
    }

    @Operation(summary = "新增对象存储配置档案")
    @PostMapping("/configs")
    public Result<StorageConfigSummaryVO> createConfig(@Valid @RequestBody StorageConfigRequest request) {
        return Result.ok(selectionService.create(request, currentUser.id()));
    }

    @Operation(summary = "更新对象存储配置档案")
    @PutMapping("/configs/{id}")
    public Result<StorageConfigSummaryVO> updateConfig(@PathVariable Long id,
                                                        @Valid @RequestBody StorageConfigRequest request) {
        return Result.ok(selectionService.update(id, request, currentUser.id()));
    }

    @Operation(summary = "删除对象存储配置档案")
    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        selectionService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "验证对象存储配置档案")
    @PostMapping("/configs/{id}/validate")
    @AccessLimit(seconds = 60, maxCount = 5)
    public Result<StorageValidationVO> validateConfig(@PathVariable Long id) {
        return Result.ok(selectionService.validate(id));
    }

    @Operation(summary = "启用对象存储配置档案")
    @PostMapping("/configs/{id}/activate")
    @AccessLimit(seconds = 60, maxCount = 5)
    public Result<StorageConfigSummaryVO> activateConfig(@PathVariable Long id) {
        return Result.ok(selectionService.activate(id, currentUser.id()));
    }

    @Operation(summary = "刷新对象存储配置档案用量")
    @PostMapping("/configs/{id}/usage")
    @AccessLimit(seconds = 60, maxCount = 5)
    public Result<StorageUsageVO> refreshUsage(@PathVariable Long id) {
        return Result.ok(selectionService.refreshUsage(id));
    }

}
