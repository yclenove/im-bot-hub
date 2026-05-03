package com.sov.imhub.ai;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能推荐服务：基于用户行为和上下文推荐查询和配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 获取查询推荐。
     *
     * @param userId 用户 ID
     * @param botId 机器人 ID
     * @param limit 推荐数量
     * @return 推荐列表
     */
    public List<Recommendation> getQueryRecommendations(Long userId, Long botId, int limit) {
        List<Recommendation> recommendations = new ArrayList<>();

        // 1. 基于用户历史查询推荐
        List<Map<String, Object>> userHistory = jdbcTemplate.queryForList(
                """
                SELECT command, COUNT(*) as usage_count
                FROM t_command_log
                WHERE external_user_id = :userId AND bot_id = :botId
                GROUP BY command
                ORDER BY usage_count DESC
                LIMIT 5
                """,
                Map.of("userId", String.valueOf(userId), "botId", botId));

        for (Map<String, Object> row : userHistory) {
            Recommendation rec = new Recommendation();
            rec.setType("QUERY");
            rec.setName((String) row.get("command"));
            rec.setReason("基于您的历史查询");
            rec.setScore(0.9);
            recommendations.add(rec);
        }

        // 2. 基于热门查询推荐
        List<Map<String, Object>> popularQueries = jdbcTemplate.queryForList(
                """
                SELECT command, COUNT(*) as total_count
                FROM t_command_log
                WHERE bot_id = :botId AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                GROUP BY command
                ORDER BY total_count DESC
                LIMIT :limit
                """,
                Map.of("botId", botId, "limit", limit));

        for (Map<String, Object> row : popularQueries) {
            String command = (String) row.get("command");
            // 避免重复
            if (recommendations.stream().noneMatch(r -> r.getName().equals(command))) {
                Recommendation rec = new Recommendation();
                rec.setType("QUERY");
                rec.setName(command);
                rec.setReason("热门查询");
                rec.setScore(0.7);
                recommendations.add(rec);
            }
        }

        return recommendations.subList(0, Math.min(recommendations.size(), limit));
    }

    /**
     * 获取参数补全建议。
     *
     * @param command 命令名
     * @param partialParam 部分参数
     * @return 补全建议
     */
    public List<String> getParamSuggestions(String command, String partialParam) {
        List<String> suggestions = new ArrayList<>();

        // 从历史查询中提取参数
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                """
                SELECT detail
                FROM t_command_log
                WHERE command = :command AND detail IS NOT NULL
                ORDER BY created_at DESC
                LIMIT 100
                """,
                Map.of("command", command));

        for (Map<String, Object> row : history) {
            String detail = (String) row.get("detail");
            if (detail != null && detail.contains(partialParam)) {
                // 提取参数值
                String[] parts = detail.split(";");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.contains("=")) {
                        String value = trimmed.split("=")[1].trim();
                        if (value.contains(partialParam) && !suggestions.contains(value)) {
                            suggestions.add(value);
                        }
                    }
                }
            }
        }

        return suggestions.subList(0, Math.min(suggestions.size(), 10));
    }

    /**
     * 获取优化建议。
     *
     * @param queryId 查询 ID
     * @return 优化建议
     */
    public List<OptimizationSuggestion> getOptimizationSuggestions(Long queryId) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // 1. 检查查询执行时间
        Map<String, Object> stats = jdbcTemplate.queryForMap(
                """
                SELECT AVG(duration_ms) as avg_duration, MAX(duration_ms) as max_duration
                FROM t_command_log
                WHERE query_definition_id = :queryId AND success = 1
                """,
                Map.of("queryId", queryId));

        if (stats != null && !stats.isEmpty()) {
            double avgDuration = ((Number) stats.get("avg_duration")).doubleValue();
            if (avgDuration > 5000) {
                OptimizationSuggestion suggestion = new OptimizationSuggestion();
                suggestion.setType("PERFORMANCE");
                suggestion.setTitle("查询执行时间过长");
                suggestion.setDescription("平均执行时间 " + String.format("%.0f", avgDuration) + "ms，建议优化查询或添加索引");
                suggestion.setPriority("HIGH");
                suggestions.add(suggestion);
            }
        }

        // 2. 检查成功率
        Map<String, Object> successStats = jdbcTemplate.queryForMap(
                """
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count
                FROM t_command_log
                WHERE query_definition_id = :queryId
                """,
                Map.of("queryId", queryId));

        if (successStats != null && !successStats.isEmpty()) {
            long total = ((Number) successStats.get("total")).longValue();
            long successCount = ((Number) successStats.get("success_count")).longValue();
            if (total > 0) {
                double successRate = (double) successCount / total * 100;
                if (successRate < 90) {
                    OptimizationSuggestion suggestion = new OptimizationSuggestion();
                    suggestion.setType("RELIABILITY");
                    suggestion.setTitle("查询成功率偏低");
                    suggestion.setDescription("成功率 " + String.format("%.1f", successRate) + "%，建议检查查询逻辑");
                    suggestion.setPriority("MEDIUM");
                    suggestions.add(suggestion);
                }
            }
        }

        return suggestions;
    }

    /**
     * 推荐。
     */
    @Data
    public static class Recommendation {
        private String type;
        private String name;
        private String reason;
        private double score;
    }

    /**
     * 优化建议。
     */
    @Data
    public static class OptimizationSuggestion {
        private String type;
        private String title;
        private String description;
        private String priority;
    }
}
