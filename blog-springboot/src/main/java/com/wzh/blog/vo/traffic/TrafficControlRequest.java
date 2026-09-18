package com.wzh.blog.vo.traffic;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Input shapes for the allowlisted administrator traffic controls. */
public final class TrafficControlRequest {

    private TrafficControlRequest() {
    }

    @Schema(description = "封禁或解封的来源 IP")
    public record IpRequest(
            @NotBlank(message = "IP 地址不能为空")
            @Size(max = 64, message = "IP 地址过长")
            String ip) {
    }

    @Schema(description = "封禁来源 IP")
    public record BlockRequest(
            @NotBlank(message = "IP 地址不能为空")
            @Size(max = 64, message = "IP 地址过长")
            String ip,
            @Size(max = 200, message = "封禁原因过长")
            String reason) {
    }

    @Schema(description = "来源 IP 备注")
    public record LabelRequest(
            @NotBlank(message = "IP 地址不能为空")
            @Size(max = 64, message = "IP 地址过长")
            String ip,
            @Size(max = 80, message = "IP 备注过长")
            String label) {
    }

    @Schema(description = "告警确认")
    public record AlertAckRequest(
            Long id,
            Boolean all) {
    }

    @Schema(description = "告警规则")
    public record AlertRuleRequest(
            @NotBlank(message = "规则 ID 不能为空")
            @Size(max = 64, message = "规则 ID 过长")
            String id,
            @NotBlank(message = "监控指标不能为空")
            @Size(max = 64, message = "监控指标过长")
            String metric,
            @NotNull(message = "告警阈值不能为空")
            Integer threshold,
            @NotNull(message = "观察窗口不能为空")
            Integer windowSec,
            @NotNull(message = "冷却时间不能为空")
            Integer cooldownSec,
            @Size(max = 16, message = "告警级别过长")
            String level,
            Boolean enabled,
            @Size(max = 200, message = "通知目标过长")
            String email) {
    }
}
