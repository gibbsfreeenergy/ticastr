package com.wzh.blog.controller;

import com.wzh.blog.annotation.OptLog;
import com.wzh.blog.service.PageService;
import com.wzh.blog.vo.PageVO;
import com.wzh.blog.vo.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

import static com.wzh.blog.constant.OptTypeConst.REMOVE;
import static com.wzh.blog.constant.OptTypeConst.SAVE_OR_UPDATE;

/**
 * 页面控制器
 *
 * @author yezhiqiu
 * @date 2021/08/09
 */
@Tag(name = "页面模块")
@RestController
public class PageController {
    @Autowired
    private PageService pageService;

    /**
     * 删除页面
     *
     * @param pageId 页面id
     * @return {@link Result <>}
     */
    @OptLog(optType = REMOVE)
    @Operation(summary = "删除页面")
    @Parameter(name = "pageId", description = "页面id", required = true)
    @DeleteMapping("/admin/pages/{pageId}")
    public Result<?> deletePage(@PathVariable("pageId") Integer pageId) {
        pageService.deletePage(pageId);
        return Result.ok();
    }

    /**
     * 保存或更新页面
     *
     * @param pageVO 页面信息
     * @return {@link Result<>}
     */
    @OptLog(optType = SAVE_OR_UPDATE)
    @Operation(summary = "保存或更新页面")
    @PostMapping("/admin/pages")
    public Result<?> saveOrUpdatePage(@Valid @RequestBody PageVO pageVO) {
        pageService.saveOrUpdatePage(pageVO);
        return Result.ok();
    }

    /**
     * 获取页面列表
     *
     * @return {@link Result<PageVO>}
     */
    @Operation(summary = "获取页面列表")
    @GetMapping("/admin/pages")
    public Result<List<PageVO>> listPages() {
        return Result.ok(pageService.listPages());
    }

}
