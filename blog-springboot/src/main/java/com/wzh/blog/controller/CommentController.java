package com.wzh.blog.controller;

import com.wzh.blog.annotation.OptLog;
import com.wzh.blog.dto.CommentBackDTO;
import com.wzh.blog.dto.CommentDTO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.dto.ReplyDTO;
import com.wzh.blog.service.CommentService;
import com.wzh.blog.vo.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

import static com.wzh.blog.constant.OptTypeConst.*;

/**
 * 评论控制器
 *
 * @author xiaojie
 * @date 2021/07/29
 */
@Tag(name = "评论模块")
@RestController
public class CommentController {
    @Autowired
    private CommentService commentService;

    /**
     * 查询评论
     *
     * @param commentVO 评论信息
     * @return {@link Result<CommentDTO>}
     */
    @Operation(summary = "查询评论")
    @GetMapping("/comments")
    public Result<PageResult<CommentDTO>> listComments(CommentVO commentVO) {
        return Result.ok(commentService.listComments(commentVO));
    }

    /**
     * 添加评论
     *
     * @param commentVO 评论信息
     * @return {@link Result<>}
     */
    @Operation(summary = "添加评论")
    @PostMapping("/comments")
    public Result<?> saveComment(@Valid @RequestBody CommentVO commentVO) {
        commentService.saveComment(commentVO);
        return Result.ok();
    }

    /**
     * 查询评论下的回复
     *
     * @param commentId 评论id
     * @return {@link Result<ReplyDTO>} 回复列表
     */
    @Operation(summary = "查询评论下的回复")
    @Parameter(name = "commentId", description = "评论id", required = true)
    @GetMapping("/comments/{commentId}/replies")
    public Result<List<ReplyDTO>> listRepliesByCommentId(@PathVariable("commentId") Integer commentId) {
        return Result.ok(commentService.listRepliesByCommentId(commentId));
    }

    /**
     * 评论点赞
     *
     * @param commentId 评论id
     * @return {@link Result<>}
     */
    @Operation(summary = "评论点赞")
    @PostMapping("/comments/{commentId}/like")
    public Result<?> saveCommentLike(@PathVariable("commentId") Integer commentId) {
        commentService.saveCommentLike(commentId);
        return Result.ok();
    }

    /**
     * 审核评论
     *
     * @param reviewVO 审核信息
     * @return {@link Result<>}
     */
    @OptLog(optType = UPDATE)
    @Operation(summary = "审核评论")
    @PutMapping("/admin/comments/review")
    public Result<?> updateCommentsReview(@Valid @RequestBody ReviewVO reviewVO) {
        commentService.updateCommentsReview(reviewVO);
        return Result.ok();
    }

    /**
     * 删除评论
     *
     * @param commentIdList 评论id列表
     * @return {@link Result<>}
     */
    @OptLog(optType = REMOVE)
    @Operation(summary = "删除评论")
    @DeleteMapping("/admin/comments")
    public Result<?> deleteComments(@RequestBody List<Integer> commentIdList) {
        commentService.removeByIds(commentIdList);
        return Result.ok();
    }

    /**
     * 查询后台评论
     *
     * @param condition 条件
     * @return {@link Result<CommentBackDTO>} 后台评论
     */
    @Operation(summary = "查询后台评论")
    @GetMapping("/admin/comments")
    public Result<PageResult<CommentBackDTO>> listCommentBackDTO(CommentQueryVO condition) {
        return Result.ok(commentService.listCommentBackDTO(condition));
    }

}

