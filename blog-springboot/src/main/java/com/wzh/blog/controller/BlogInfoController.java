package com.wzh.blog.controller;

import com.wzh.blog.dto.BlogBackInfoDTO;
import com.wzh.blog.dto.BlogHomeInfoDTO;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.service.BlogInfoService;
import com.wzh.blog.vo.BlogInfoVO;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.WebsiteConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


/**
 * 首页、关于页和网站配置控制器。
 */
@Tag(name = "博客信息模块")
@RestController
public class BlogInfoController {

    private final BlogInfoService blogInfoService;
    private final MediaAssetStore mediaAssetStore;

    public BlogInfoController(BlogInfoService blogInfoService, MediaAssetStore mediaAssetStore) {
        this.blogInfoService = blogInfoService;
        this.mediaAssetStore = mediaAssetStore;
    }

    @Operation(summary = "查看博客信息")
    @GetMapping("/")
    public Result<BlogHomeInfoDTO> getBlogHomeInfo() {
        return Result.ok(blogInfoService.getBlogHomeInfo());
    }

    @Operation(summary = "查看后台信息")
    @GetMapping("/admin")
    public Result<BlogBackInfoDTO> getBlogBackInfo() {
        return Result.ok(blogInfoService.getBlogBackInfo());
    }

    @Operation(summary = "上传网站配置图片")
    @Parameter(name = "file", description = "图片", required = true)
    @PostMapping("/admin/config/images")
    public Result<String> saveConfigImage(MultipartFile file) {
        return Result.ok(mediaAssetStore.upload(file, FilePathEnum.CONFIG.getPath()));
    }

    @Operation(summary = "更新网站配置")
    @PutMapping("/admin/website/config")
    public Result<?> updateWebsiteConfig(@Valid @RequestBody WebsiteConfigVO websiteConfigVO) {
        blogInfoService.updateWebsiteConfig(websiteConfigVO);
        return Result.ok();
    }

    @Operation(summary = "获取网站配置")
    @GetMapping("/admin/website/config")
    public Result<WebsiteConfigVO> getWebsiteConfig() {
        return Result.ok(blogInfoService.getWebsiteConfig());
    }

    @Operation(summary = "查看关于我信息")
    @GetMapping("/about")
    public Result<String> getAbout() {
        return Result.ok(blogInfoService.getAbout());
    }

    @Operation(summary = "修改关于我信息")
    @PutMapping("/admin/about")
    public Result<?> updateAbout(@Valid @RequestBody BlogInfoVO blogInfoVO) {
        blogInfoService.updateAbout(blogInfoVO);
        return Result.ok();
    }
}
