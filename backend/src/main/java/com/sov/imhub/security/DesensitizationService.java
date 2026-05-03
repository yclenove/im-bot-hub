package com.sov.imhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据脱敏服务：敏感数据自动脱敏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesensitizationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 对数据进行脱敏处理。
     *
     * @param data 原始数据
     * @param userRole 用户角色（决定脱敏级别）
     * @return 脱敏后的数据
     */
    public List<Map<String, Object>> desensitize(List<Map<String, Object>> data, String userRole) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        // SUPER_ADMIN 不脱敏
        if ("SUPER_ADMIN".equals(userRole)) {
            return data;
        }

        // 获取脱敏规则
        List<DesensitizationRule> rules = getRules();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : data) {
            result.add(desensitizeRow(row, rules));
        }
        return result;
    }

    /**
     * 对单行数据进行脱敏。
     */
    private Map<String, Object> desensitizeRow(Map<String, Object> row, List<DesensitizationRule> rules) {
        Map<String, Object> result = new java.util.HashMap<>(row);

        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String strValue) {
                for (DesensitizationRule rule : rules) {
                    if (matchesPattern(fieldName, rule.getFieldPattern())) {
                        result.put(fieldName, applyMask(strValue, rule));
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * 获取脱敏规则。
     */
    private List<DesensitizationRule> getRules() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM t_desensitization_rule WHERE enabled = 1", Map.of());

        List<DesensitizationRule> rules = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DesensitizationRule rule = new DesensitizationRule();
            rule.setName((String) row.get("name"));
            rule.setFieldPattern((String) row.get("field_pattern"));
            rule.setMaskType((String) row.get("mask_type"));

            try {
                String configJson = (String) row.get("mask_config");
                if (configJson != null) {
                    rule.setMaskConfig(objectMapper.readValue(configJson, Map.class));
                }
            } catch (Exception e) {
                log.warn("parse mask config failed: {}", e.getMessage());
            }

            rules.add(rule);
        }
        return rules;
    }

    /**
     * 检查字段名是否匹配模式。
     */
    private boolean matchesPattern(String fieldName, String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(fieldName).matches();
        } catch (Exception e) {
            return fieldName.toLowerCase().contains(pattern.toLowerCase());
        }
    }

    /**
     * 应用脱敏。
     */
    private String applyMask(String value, DesensitizationRule rule) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Map<String, Object> config = rule.getMaskConfig();
        if (config == null) {
            return "***";
        }

        switch (rule.getMaskType()) {
            case "PARTIAL":
                return applyPartialMask(value, config);
            case "FULL":
                return "***";
            case "HASH":
                return String.valueOf(value.hashCode());
            case "MASK":
                return applyCustomMask(value, config);
            default:
                return "***";
        }
    }

    /**
     * 部分脱敏。
     */
    private String applyPartialMask(String value, Map<String, Object> config) {
        int prefix = config.containsKey("prefix") ? ((Number) config.get("prefix")).intValue() : 0;
        int suffix = config.containsKey("suffix") ? ((Number) config.get("suffix")).intValue() : 0;
        String mask = config.containsKey("mask") ? (String) config.get("mask") : "*";

        if (value.length() <= prefix + suffix) {
            return value;
        }

        StringBuilder result = new StringBuilder();
        result.append(value, 0, prefix);
        for (int i = 0; i < value.length() - prefix - suffix; i++) {
            result.append(mask);
        }
        result.append(value, value.length() - suffix, value.length());
        return result.toString();
    }

    /**
     * 自定义脱敏。
     */
    private String applyCustomMask(String value, Map<String, Object> config) {
        String mask = config.containsKey("mask") ? (String) config.get("mask") : "***";
        return mask;
    }

    /**
     * 脱敏规则。
     */
    @Data
    public static class DesensitizationRule {
        private String name;
        private String fieldPattern;
        private String maskType;
        private Map<String, Object> maskConfig;
    }
}
