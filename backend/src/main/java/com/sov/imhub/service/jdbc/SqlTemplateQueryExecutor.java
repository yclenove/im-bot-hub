package com.sov.imhub.service.jdbc;

import com.sov.imhub.service.QueryParamSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binds MyBatis-style #{name} templates to Spring {@link NamedParameterJdbcTemplate} (:name).
 *
 * <p>Same semantics as MyBatis {@code #{}} parameterization (prepared statements, no value concatenation).</p>
 */
@Service
@RequiredArgsConstructor
public class SqlTemplateQueryExecutor {

    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(\\w+)}");

    private final SqlTemplateValidator validator;

    public List<Map<String, Object>> query(
            NamedParameterJdbcTemplate jdbc,
            String sqlTemplate,
            Map<String, Object> params,
            int maxRows,
            int timeoutSeconds
    ) {
        validator.validate(sqlTemplate);
        String namedSql = toNamedParameters(sqlTemplate.trim());
        jdbc.getJdbcTemplate().setQueryTimeout(timeoutSeconds);
        List<Map<String, Object>> rows = jdbc.queryForList(limitSql(namedSql, maxRows), params);
        return rows;
    }

    private static String toNamedParameters(String sql) {
        Matcher m = PLACEHOLDER.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, ":" + m.group(1));
        }
        m.appendTail(sb);
        String out = sb.toString();
        if (out.contains("#{")) {
            throw new IllegalArgumentException("Unconverted placeholders remain");
        }
        return out;
    }

    private static String limitSql(String sql, int maxRows) {
        if (maxRows <= 0) {
            return sql;
        }
        String s = sql.trim();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s + " LIMIT " + maxRows;
    }

    /** Parse param_schema_json like {"params":["orderNo","id"]} or empty -> default {@code orderNo}. */
    public Map<String, Object> buildParamMap(List<String> argValues, String paramSchemaJson) {
        List<String> names = new ArrayList<>(QueryParamSchema.parseParamNames(paramSchemaJson));
        if (names.isEmpty()) {
            names.add("orderNo");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String val = i < argValues.size() ? argValues.get(i) : null;
            m.put(names.get(i), val);
        }
        return m;
    }
}
