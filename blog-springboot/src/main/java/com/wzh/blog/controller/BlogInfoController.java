package com.wzh.blog.controller;


import com.wzh.blog.annotation.OptLog;
import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.dto.BlogBackInfoDTO;
import com.wzh.blog.dto.BlogHomeInfoDTO;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.BlogInfoService;
import com.wzh.blog.service.ChatApplicationService;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.util.IpUtils;
import com.wzh.blog.vo.BlogInfoVO;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.VoiceVO;
import com.wzh.blog.vo.WebsiteConfigVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import static com.wzh.blog.constant.OptTypeConst.UPDATE;

/**
 * 博客信息控制器
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Tag(name = "博客信息模块")
@RestController
public class BlogInfoController {
    private final BlogInfoService blogInfoService;
    private final ChatApplicationService chatApplicationService;
    private final MediaAssetStore mediaAssetStore;
    private final HttpServletRequest request;
    private final CurrentUser currentUser;

    public BlogInfoController(BlogInfoService blogInfoService,
                              ChatApplicationService chatApplicationService,
                              MediaAssetStore mediaAssetStore,
                              HttpServletRequest request,
                              CurrentUser currentUser) {
        this.blogInfoService = blogInfoService;
        this.chatApplicationService = chatApplicationService;
        this.mediaAssetStore = mediaAssetStore;
        this.request = request;
        this.currentUser = currentUser;
    }

    /**
     * 查看博客信息
     *
     * @return {@link Result<BlogHomeInfoDTO>} 博客信息
     */
    @Operation(summary = "查看博客信息")
    @GetMapping("/")
    public Result<BlogHomeInfoDTO> getBlogHomeInfo() {
        return Result.ok(blogInfoService.getBlogHomeInfo());
    }

    /**
     * 查看后台信息
     *
     * @return {@link Result<BlogBackInfoDTO>} 后台信息
     */
    @Operation(summary = "查看后台信息")
    @GetMapping("/admin")
    public Result<BlogBackInfoDTO> getBlogBackInfo() {
        return Result.ok(blogInfoService.getBlogBackInfo());
    }

    /**
     * 上传博客配置图片
     *
     * @param file 文件
     * @return {@link Result<String>} 博客配置图片
     */
    @Operation(summary = "上传博客配置图片")
    @Parameter(name = "file", description = "图片", required = true)
    @PostMapping("/admin/config/images")
    public Result<String> savePhotoAlbumCover(MultipartFile file) {
        return Result.ok(mediaAssetStore.upload(file, FilePathEnum.CONFIG.getPath()));
    }

    /**
     * 更新网站配置
     *
     * @param websiteConfigVO 网站配置信息
     * @return {@link Result}
     */
    @Operation(summary = "更新网站配置")
    @PutMapping("/admin/website/config")
    public Result<?> updateWebsiteConfig(@Valid @RequestBody WebsiteConfigVO websiteConfigVO) {
        blogInfoService.updateWebsiteConfig(websiteConfigVO);
        return Result.ok();
    }

    /**
     * 获取网站配置
     *
     * @return {@link Result<WebsiteConfigVO>} 网站配置
     */
    @Operation(summary = "获取网站配置")
    @GetMapping("/admin/website/config")
    public Result<WebsiteConfigVO> getWebsiteConfig() {
        return Result.ok(blogInfoService.getWebsiteConfig());
    }

    /**
     * 查看关于我信息
     *
     * @return {@link Result<String>} 关于我信息
     */
    @Operation(summary = "查看关于我信息")
    @GetMapping("/about")
    public Result<String> getAbout() {
        return Result.ok(blogInfoService.getAbout());
    }

    /**
     * 修改关于我信息
     *
     * @param blogInfoVO 博客信息
     * @return {@link Result<>}
     */
    @OptLog(optType = UPDATE)
    @Operation(summary = "修改关于我信息")
    @PutMapping("/admin/about")
    public Result<?> updateAbout(@Valid @RequestBody BlogInfoVO blogInfoVO) {
        blogInfoService.updateAbout(blogInfoVO);
        return Result.ok();
    }

    /**
     * 保存语音信息
     *
     * @param voiceVO 语音信息
     * @return {@link Result<String>} 语音地址
     */
    @Operation(summary = "上传语音")
    @AccessLimit(seconds = 60, maxCount = 10)
    @PostMapping("/voice")
    public Result<String> sendVoice(VoiceVO voiceVO) {
        String ipAddress = IpUtils.getIpAddress(request);
        applyVoiceSender(voiceVO);
        voiceVO.setIpAddress(ipAddress);
        voiceVO.setIpSource(IpUtils.getIpSource(ipAddress));
        chatApplicationService.sendVoice(voiceVO);
        return Result.ok();
    }

    private void applyVoiceSender(VoiceVO voiceVO) {
        currentUser.find().ifPresentOrElse(userDetail -> {
            voiceVO.setUserId(userDetail.getUserInfoId());
            voiceVO.setNickname(userDetail.getNickname());
            voiceVO.setAvatar(userDetail.getAvatar());
        }, () -> {
            voiceVO.setUserId(null);
            voiceVO.setNickname("游客");
            voiceVO.setAvatar(blogInfoService.getWebsiteConfig().getTouristAvatar());
        });
    }

    /**
     * 上传访客信息
     *
     * @return {@link Result}
     */
    @PostMapping("/report")
    @AccessLimit(seconds = 60, maxCount = 20)
    public Result<?> report() {
        blogInfoService.report();
        return Result.ok();
    }

}

