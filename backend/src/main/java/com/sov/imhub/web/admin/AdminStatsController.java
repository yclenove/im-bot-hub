package com.sov.imhub.web.admin;

import com.sov.imhub.service.CommandStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 命令统计 API。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final CommandStatsService statsService;

    /**
     * 获取命令统计（按日期）。
     */
    @GetMapping("/commands")
    public List<Map<String, Object>> getCommandStats(
            @RequestParam Long botId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return statsService.getStats(botId, startDate, endDate);
    }

    /**
     * 获取热门命令排行。
     */
    @GetMapping("/top-commands")
    public List<Map<String, Object>> getTopCommands(
            @RequestParam Long botId,
            @RequestParam(defaultValue = "10") int limit) {
        return statsService.getTopCommands(botId, limit);
    }

    /**
     * 获取平台维度统计。
     */
    @GetMapping("/platforms")
    public List<Map<String, Object>> getPlatformStats(@RequestParam Long botId) {
        return statsService.getPlatformStats(botId);
    }
}
