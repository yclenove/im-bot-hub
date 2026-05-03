package com.sov.imhub.web.admin;

import com.sov.imhub.service.ChannelHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 渠道健康检查 API。
 */
@RestController
@RequestMapping("/api/admin/channel-health")
@RequiredArgsConstructor
public class AdminChannelHealthController {

    private final ChannelHealthService healthService;

    /**
     * 获取所有渠道的健康状态。
     */
    @GetMapping
    public Map<Long, ChannelHealthService.HealthStatus> getAllHealth() {
        return healthService.getAllHealthStatus();
    }

    /**
     * 获取指定渠道的健康状态。
     */
    @GetMapping("/{channelId}")
    public ChannelHealthService.HealthStatus getChannelHealth(@PathVariable Long channelId) {
        return healthService.getChannelHealth(channelId);
    }

    /**
     * 获取渠道健康历史。
     */
    @GetMapping("/{channelId}/history")
    public List<Map<String, Object>> getHealthHistory(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "20") int limit) {
        return healthService.getHealthHistory(channelId, limit);
    }

    /**
     * 手动触发健康检查。
     */
    @PostMapping("/check")
    public Map<String, Object> triggerCheck() {
        healthService.checkAllChannels();
        return Map.of("success", true, "message", "健康检查已触发");
    }
}
