package com.sov.imhub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.domain.*;
import com.sov.imhub.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘统计服务：提供概览页面所需的聚合统计数据。
 */
@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final BotMapper botMapper;
    private final BotChannelMapper botChannelMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final CommandLogMapper commandLogMapper;

    /**
     * 获取概览统计数据。
     */
    public Map<String, Object> getStats() {
        Map<String, Object> r = new HashMap<>();

        // 机器人统计
        r.put("botTotal", botMapper.selectCount(new LambdaQueryWrapper<Bot>()));
        r.put("botEnabled", botMapper.selectCount(new LambdaQueryWrapper<Bot>().eq(Bot::getEnabled, true)));

        // 渠道统计
        r.put("channelTotal", botChannelMapper.selectCount(new LambdaQueryWrapper<BotChannelEntity>()));

        // 查询定义统计
        r.put("queryTotal", queryDefinitionMapper.selectCount(new LambdaQueryWrapper<QueryDefinitionEntity>()));
        r.put("queryEnabled", queryDefinitionMapper.selectCount(
                new LambdaQueryWrapper<QueryDefinitionEntity>().eq(QueryDefinitionEntity::getEnabled, true)));

        // 今日命令统计
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long todayTotal = commandLogMapper.selectCount(
                new LambdaQueryWrapper<CommandLogEntity>().ge(CommandLogEntity::getCreatedAt, todayStart));
        long todaySuccess = commandLogMapper.selectCount(
                new LambdaQueryWrapper<CommandLogEntity>()
                        .ge(CommandLogEntity::getCreatedAt, todayStart)
                        .eq(CommandLogEntity::getSuccess, true));
        r.put("todayCommandTotal", todayTotal);
        r.put("todayCommandSuccess", todaySuccess);
        r.put("todayCommandFailed", todayTotal - todaySuccess);

        // 渠道按平台统计
        List<BotChannelEntity> allChannels = botChannelMapper.selectList(new LambdaQueryWrapper<BotChannelEntity>());
        Map<String, Long> platformCounts = allChannels.stream()
                .collect(Collectors.groupingBy(c -> c.getPlatform() == null ? "UNKNOWN" : c.getPlatform(), Collectors.counting()));
        r.put("channelByPlatform", platformCounts);

        return r;
    }

    /**
     * 获取最近 7 天命令趋势。
     */
    public List<Map<String, Object>> getTrend() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(date.plusDays(1), LocalTime.MIN);
            long total = commandLogMapper.selectCount(
                    new LambdaQueryWrapper<CommandLogEntity>()
                            .ge(CommandLogEntity::getCreatedAt, dayStart)
                            .lt(CommandLogEntity::getCreatedAt, dayEnd));
            long success = commandLogMapper.selectCount(
                    new LambdaQueryWrapper<CommandLogEntity>()
                            .ge(CommandLogEntity::getCreatedAt, dayStart)
                            .lt(CommandLogEntity::getCreatedAt, dayEnd)
                            .eq(CommandLogEntity::getSuccess, true));
            Map<String, Object> day = new HashMap<>();
            day.put("date", date.format(fmt));
            day.put("total", total);
            day.put("success", success);
            day.put("failed", total - success);
            result.add(day);
        }
        return result;
    }

    /**
     * 获取最近命令日志。
     */
    public List<Map<String, Object>> getRecentLogs() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return commandLogMapper.selectList(
                new LambdaQueryWrapper<CommandLogEntity>()
                        .orderByDesc(CommandLogEntity::getId)
                        .last("LIMIT 5"))
                .stream()
                .map(l -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", l.getId());
                    m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().format(fmt) : "");
                    m.put("platform", l.getPlatform());
                    m.put("command", l.getCommand());
                    m.put("success", Boolean.TRUE.equals(l.getSuccess()));
                    m.put("durationMs", l.getDurationMs());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
