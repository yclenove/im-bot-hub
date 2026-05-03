package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 机器人详情响应：包含关联的渠道、查询定义和最近命令日志。
 */
@Value
@Builder
public class BotDetailResponse {
    Long id;
    String name;
    boolean enabled;
    List<ChannelSummary> channels;
    List<QuerySummary> queries;
    List<LogSummary> recentLogs;

    @Value
    @Builder
    public static class ChannelSummary {
        Long id;
        String platform;
        String name;
        boolean enabled;
    }

    @Value
    @Builder
    public static class QuerySummary {
        Long id;
        String command;
        String name;
        String queryMode;
        Long datasourceId;
        boolean enabled;
    }

    @Value
    @Builder
    public static class LogSummary {
        Long id;
        String createdAt;
        String platform;
        String command;
        boolean success;
        String errorKind;
        Integer durationMs;
    }
}
