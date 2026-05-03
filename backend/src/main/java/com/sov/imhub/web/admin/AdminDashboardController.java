package com.sov.imhub.web.admin;

import com.sov.imhub.service.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计 API：提供概览页面所需的统计数据。
 *
 * <p>包含以下端点：</p>
 * <ul>
 *   <li>{@code GET /stats} — 机器人/渠道/查询/今日命令统计</li>
 *   <li>{@code GET /trend} — 最近 7 天命令趋势（每天成功/失败数）</li>
 *   <li>{@code GET /recent-logs} — 最近 5 条命令日志</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardStatsService dashboardStatsService;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return dashboardStatsService.getStats();
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> trend() {
        return dashboardStatsService.getTrend();
    }

    @GetMapping("/recent-logs")
    public List<Map<String, Object>> recentLogs() {
        return dashboardStatsService.getRecentLogs();
    }
}
