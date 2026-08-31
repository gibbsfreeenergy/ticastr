package com.wzh.blog.controller;

import com.wzh.blog.administration.StorageProviderAdminService;
import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.annotation.OptLog;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.StorageProviderSelectionResponse;
import com.wzh.blog.vo.StorageProviderStatusVO;
import com.wzh.blog.vo.StorageProviderValidationVO;
import com.wzh.blog.vo.StorageProviderSwitchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.wzh.blog.constant.OptTypeConst.UPDATE;

@Tag(name = "对象存储配置")
@RestController
@RequestMapping("/admin/storage")
public class StorageProviderController {

    private final StorageProviderAdminService selectionService;
    private final CurrentUser currentUser;

    public StorageProviderController(StorageProviderAdminService selectionService,
                                     CurrentUser currentUser) {
        this.selectionService = selectionService;
        this.currentUser = currentUser;
    }

    @Operation(summary = "查看当前对象存储 provider")
    @GetMapping("/provider")
    public Result<StorageProviderSelectionResponse> currentProvider() {
        return Result.ok(selectionService.current());
    }

    @Operation(summary = "查看对象存储 provider 状态")
    @GetMapping("/providers")
    public Result<java.util.List<StorageProviderStatusVO>> providers() {
        return Result.ok(selectionService.providers());
    }

    @Operation(summary = "验证对象存储 provider")
    @PostMapping("/providers/{provider}/validate")
    @AccessLimit(seconds = 60, maxCount = 5)
    public Result<StorageProviderValidationVO> validate(@PathVariable String provider) {
        return Result.ok(selectionService.validateProvider(provider));
    }

    @OptLog(optType = UPDATE)
    @Operation(summary = "切换对象存储 provider")
    @PutMapping("/provider")
    @AccessLimit(seconds = 60, maxCount = 5)
    public Result<StorageProviderSelectionResponse> switchProvider(
            @Valid @RequestBody StorageProviderSwitchRequest request) {
        return Result.ok(selectionService.switchProvider(request.provider(), currentUser.id()));
    }
}
