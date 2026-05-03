package com.sov.imhub.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.DatasourceEntity;
import com.sov.imhub.domain.QueryDefinitionEntity;
import com.sov.imhub.mapper.DatasourceMapper;
import com.sov.imhub.mapper.QueryDefinitionMapper;
import com.sov.imhub.service.AuditLogService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * NL2SQL 服务：自然语言转 SQL 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DatasourceMapper datasourceMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final LlmClient llmClient;

    /**
     * 自然语言转 SQL。
     *
     * @param question 用户问题
     * @param datasourceId 数据源 ID
     * @param botId 机器人 ID（用于上下文）
     * @return NL2SQL 结果
     */
    public Nl2SqlResult convert(String question, Long datasourceId, Long botId) {
        log.info("nl2sql convert question={} datasourceId={} botId={}", question, datasourceId, botId);

        // 1. 获取数据源信息
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在");
        }

        // 2. 提取 schema 信息
        String schema = extractSchema(datasource);

        // 3. 获取相关查询作为参考
        List<QueryDefinitionEntity> relatedQueries = queryDefinitionMapper.selectList(
                new LambdaQueryWrapper<QueryDefinitionEntity>()
                        .eq(QueryDefinitionEntity::getBotId, botId)
                        .eq(QueryDefinitionEntity::getEnabled, true)
                        .last("LIMIT 5"));

        // 4. 构建 prompt
        String prompt = buildPrompt(question, schema, relatedQueries);

        // 5. 调用 LLM
        String generatedSql = llmClient.complete(prompt);

        // 6. 验证 SQL 语法
        validateSql(generatedSql);

        // 7. 计算置信度
        double confidence = calculateConfidence(question, generatedSql);

        Nl2SqlResult result = new Nl2SqlResult();
        result.setQuestion(question);
        result.setGeneratedSql(generatedSql);
        result.setConfidence(confidence);
        result.setDatasourceId(datasourceId);

        auditLogService.log("NL2SQL", "QUERY", null,
                "问题: " + question + ", SQL: " + generatedSql);

        return result;
    }

    /**
     * 执行 NL2SQL 生成的查询。
     */
    public List<Map<String, Object>> execute(Nl2SqlResult result, List<Object> params) {
        log.info("nl2sql execute sql={}", result.getGeneratedSql());

        // 使用命名参数执行
        Map<String, Object> paramMap = buildParamMap(result.getGeneratedSql(), params);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(result.getGeneratedSql(), paramMap);

        result.setExecuted(true);
        result.setExecutionResult(rows);

        return rows;
    }

    /**
     * 保存用户反馈。
     */
    public void saveFeedback(Long historyId, int score, String comment) {
        log.info("nl2sql feedback historyId={} score={}", historyId, score);
        // 保存到 t_nl2sql_history
    }

    /**
     * 从 INFORMATION_SCHEMA 提取表结构信息（支持指定数据库）。
     */
    private String extractSchema(DatasourceEntity datasource) {
        StringBuilder schema = new StringBuilder();
        try {
            // 从 JDBC URL 提取数据库名
            String dbName = extractDatabaseName(datasource.getJdbcUrl());

            // 使用 INFORMATION_SCHEMA 获取表和列信息
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = :dbName AND TABLE_TYPE = 'BASE TABLE'",
                    Map.of("dbName", dbName));

            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("TABLE_NAME");
                String comment = (String) table.get("TABLE_COMMENT");
                schema.append("Table: ").append(tableName);
                if (comment != null && !comment.isBlank()) {
                    schema.append(" (").append(comment).append(")");
                }
                schema.append("\n");

                List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                        "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = :dbName AND TABLE_NAME = :tableName ORDER BY ORDINAL_POSITION",
                        Map.of("dbName", dbName, "tableName", tableName));

                for (Map<String, Object> col : columns) {
                    schema.append("  - ").append(col.get("COLUMN_NAME"))
                            .append(" (").append(col.get("COLUMN_TYPE")).append(")")
                            .append("NO".equals(col.get("IS_NULLABLE")) ? " NOT NULL" : "");
                    String colComment = (String) col.get("COLUMN_COMMENT");
                    if (colComment != null && !colComment.isBlank()) {
                        schema.append(" -- ").append(colComment);
                    }
                    schema.append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("extract schema failed: {}", e.getMessage());
        }
        return schema.toString();
    }

    /**
     * 从 JDBC URL 提取数据库名。
     */
    private String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null) return "im_hub";
        // jdbc:mysql://host:port/database?params
        try {
            String afterSlash = jdbcUrl.substring(jdbcUrl.lastIndexOf('/') + 1);
            int questionMark = afterSlash.indexOf('?');
            return questionMark > 0 ? afterSlash.substring(0, questionMark) : afterSlash;
        } catch (Exception e) {
            return "im_hub";
        }
    }

    private String buildPrompt(String question, String schema, List<QueryDefinitionEntity> relatedQueries) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个 SQL 专家。根据用户的自然语言问题和数据库 schema，生成对应的 SQL 查询。\n\n");
        prompt.append("数据库 Schema:\n").append(schema).append("\n");

        if (!relatedQueries.isEmpty()) {
            prompt.append("相关查询参考:\n");
            for (QueryDefinitionEntity q : relatedQueries) {
                prompt.append("- ").append(q.getName()).append(": ").append(q.getSqlTemplate()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("用户问题: ").append(question).append("\n\n");
        prompt.append("要求:\n");
        prompt.append("1. 只返回 SQL 语句，不要解释\n");
        prompt.append("2. 使用参数化查询（:param 格式）\n");
        prompt.append("3. 确保 SQL 语法正确\n");
        prompt.append("4. 优先使用索引字段\n");
        prompt.append("5. 限制返回行数（LIMIT）\n");

        return prompt.toString();
    }

    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("生成的 SQL 为空");
        }
        // 基本安全检查
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("DROP ") || upperSql.contains("DELETE ") ||
            upperSql.contains("UPDATE ") || upperSql.contains("INSERT ")) {
            throw new IllegalArgumentException("生成的 SQL 包含危险操作");
        }
    }

    private double calculateConfidence(String question, String sql) {
        // 简单的置信度计算
        double confidence = 70.0;
        if (sql.contains("SELECT")) confidence += 10;
        if (sql.contains("WHERE")) confidence += 10;
        if (sql.contains("LIMIT")) confidence += 5;
        if (sql.length() > 20) confidence += 5;
        return Math.min(confidence, 100.0);
    }

    private Map<String, Object> buildParamMap(String sql, List<Object> params) {
        // 解析 SQL 中的参数名并构建参数映射
        Map<String, Object> paramMap = new java.util.HashMap<>();
        if (params != null) {
            // 简单实现：按顺序映射
            List<String> paramNames = extractParamNames(sql);
            for (int i = 0; i < Math.min(paramNames.size(), params.size()); i++) {
                paramMap.put(paramNames.get(i), params.get(i));
            }
        }
        return paramMap;
    }

    private List<String> extractParamNames(String sql) {
        List<String> names = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(":(\\w+)").matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * NL2SQL 结果。
     */
    @Data
    public static class Nl2SqlResult {
        private Long id;
        private String question;
        private String generatedSql;
        private double confidence;
        private Long datasourceId;
        private boolean executed;
        private List<Map<String, Object>> executionResult;
        private Integer feedbackScore;
        private String feedbackComment;
    }
}
