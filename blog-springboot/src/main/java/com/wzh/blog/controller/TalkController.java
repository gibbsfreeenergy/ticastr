package com.wzh.blog.controller;

import com.wzh.blog.dto.TalkBackDTO;
import com.wzh.blog.dto.TalkDTO;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.service.TalkService;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.vo.StatusQueryVO;
import com.wzh.blog.vo.PageQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.TalkVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 说说控制器
 *
 * @author yezhiqiu
 * @date 2022/01/23
 */
@Tag(name = "说说模块")
@RestController
public class TalkController {
    private final TalkService talkService;
    private final MediaAssetStore mediaAssetStore;

    public TalkController(TalkService talkService, MediaAssetStore mediaAssetStore) {
        this.talkService = talkService;
        this.mediaAssetStore = mediaAssetStore;
    }

    /**
     * 查看首页说说
     *
     * @return {@link Result<String>}
     */
    @Operation(summary = "查看首页说说")
    @GetMapping("/home/talks")
    public Result<List<String>> listHomeTalks() {
        return Result.ok(talkService.listHomeTalks());
    }

    /**
     * 查看说说列表
     *
     * @return {@link Result<TalkDTO>}
     */
    @Operation(summary = "查看说说列表")
    @GetMapping("/talks")
    public Result<PageResult<TalkDTO>> listTalks(PageQueryVO pageQueryVO) {
        return Result.ok(talkService.listTalks(pageQueryVO.toPageQuery()));
    }

    /**
     * 根据id查看说说
     *
     * @param talkId 说说id
     * @return {@link Result<TalkDTO>}
     */
    @Operation(summary = "根据id查看说说")
    @Parameter(name = "talkId", description = "说说id", required = true)
    @GetMapping("/talks/{talkId}")
    public Result<TalkDTO> getTalkById(@PathVariable("talkId") Integer talkId) {
        return Result.ok(talkService.getTalkById(talkId));
    }

    /**
     * 点赞说说
     *
     * @param talkId 说说id
     * @return {@link Result<>}
     */
    @Operation(summary = "点赞说说")
    @Parameter(name = "talkId", description = "说说id", required = true)
    @PostMapping("/talks/{talkId}/like")
    public Result<?> saveTalkLike(@PathVariable("talkId") Integer talkId) {
        talkService.saveTalkLike(talkId);
        return Result.ok();
    }

    /**
     * 上传说说图片
     *
     * @param file 文件
     * @return {@link Result<String>} 说说图片地址
     */
    @Operation(summary = "上传说说图片")
    @Parameter(name = "file", description = "说说图片", required = true)
    @PostMapping("/admin/talks/images")
    public Result<String> saveTalkImages(MultipartFile file) {
        return Result.ok(mediaAssetStore.upload(file, FilePathEnum.TALK.getPath()));
    }

    /**
     * 保存或修改说说
     *
     * @param talkVO 说说信息
     * @return {@link Result<>}
     */
    @Operation(summary = "保存或修改说说")
    @PostMapping("/admin/talks")
    public Result<?> saveOrUpdateTalk(@Valid @RequestBody TalkVO talkVO) {
        talkService.saveOrUpdateTalk(talkVO);
        return Result.ok();
    }

    /**
     * 删除说说
     *
     * @param talkIdList 说说id列表
     * @return {@link Result<>}
     */
    @Operation(summary = "删除说说")
    @DeleteMapping("/admin/talks")
    public Result<?> deleteTalks(@RequestBody List<Integer> talkIdList) {
        talkService.deleteTalks(talkIdList);
        return Result.ok();
    }

    /**
     * 查看后台说说
     *
     * @param conditionVO 条件
     * @return {@link Result<TalkBackDTO>} 说说列表
     */
    @Operation(summary = "查看后台说说")
    @GetMapping("/admin/talks")
    public Result<PageResult<TalkBackDTO>> listBackTalks(StatusQueryVO conditionVO) {
        return Result.ok(talkService.listBackTalks(conditionVO, conditionVO.toPageQuery()));
    }

    /**
     * 根据id查看后台说说
     *
     * @param talkId 说说id
     * @return {@link Result<TalkDTO>}
     */
    @Operation(summary = "根据id查看后台说说")
    @Parameter(name = "talkId", description = "说说id", required = true)
    @GetMapping("/admin/talks/{talkId}")
    public Result<TalkBackDTO> getBackTalkById(@PathVariable("talkId") Integer talkId) {
        return Result.ok(talkService.getBackTalkById(talkId));
    }


}
