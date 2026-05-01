package com.sov.imhub.service.jdbc;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 管理端保存或执行前对 SQL 模板做服务端校验，降低注入与多语句风险。
 *
 * <p>注意：业务值仍必须通过 {@code #{name}} → 预编译参数绑定；本类只做字符串级拒绝明显危险片段。</p>
 *
 * <p>Validates admin-supplied SQL templates before persistence or execution to block obvious injection /
 * multi-statement patterns. Value binding via {@code #{}} placeholders remains mandatory elsewhere.</p>
 */
@Component
public class SqlTemplateValidator {

    public void validate(String sqlTemplate) {
        if (sqlTemplate == null || sqlTemplate.isBlank()) {
            throw new IllegalArgumentException("sqlTemplate is empty");
        }
        String sql = sqlTemplate.trim();
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select")) {
            throw new IllegalArgumentException("Only SELECT is allowed");
        }
        if (sql.contains(";") && sql.lastIndexOf(';') < sql.length() - 1) {
            throw new IllegalArgumentException("Multiple statements are not allowed");
        }
        for (String bad : new String[] {"--", "/*", "*/", "xp_", "information_schema"}) {
            if (sql.contains(bad)) {
                throw new IllegalArgumentException("Forbidden token: " + bad);
            }
        }
        // block obvious DML/DDL keywords as whole words-ish
        for (String bad : new String[]{" insert ", " update ", " delete ", " drop ", " alter ", " truncate ", " grant ", " revoke "}) {
            if (lower.contains(bad)) {
                throw new IllegalArgumentException("Forbidden keyword in template");
            }
        }
    }
}
