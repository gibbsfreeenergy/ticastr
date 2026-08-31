package com.wzh.blog.controller;

import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.jobs.OutboxMetrics;
import com.wzh.blog.service.OutboxEventService;
import com.wzh.blog.vo.OutboxEventAdminVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.vo.PageQueryVO;
import com.wzh.blog.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Safe operational controls for durable events. */
@Tag(name = "可靠事件")
@RestController
@RequestMapping("/admin/outbox")
public class OutboxController {

    private final OutboxEventService eventService;

    public OutboxController(OutboxEventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "查看可靠事件")
    @GetMapping
    public Result<PageResult<OutboxEventAdminVO>> list(PageQueryVO pageQuery) {
        return Result.ok(eventService.list(pageQuery.toPageQuery()));
    }

    @Operation(summary = "查看可靠事件统计")
    @GetMapping("/metrics")
    public Result<Map<String, Long>> metrics() {
        return Result.ok(eventService.metrics());
    }

    @Operation(summary = "重试可靠事件")
    @AccessLimit(seconds = 60, maxCount = 20)
    @PostMapping("/{eventId}/retry")
    public Result<?> retry(@PathVariable String eventId) {
        eventService.retryFromAdmin(eventId);
        return Result.ok();
    }
}
