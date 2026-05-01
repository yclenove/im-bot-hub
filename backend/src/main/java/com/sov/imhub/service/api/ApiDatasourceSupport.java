package com.sov.imhub.service.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.DatasourceEntity;
import com.sov.imhub.service.QueryParamSchema;
import com.sov.imhub.service.crypto.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiDatasourceSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient.Builder restClientBuilder;
    private final EncryptionService encryptionService;

    public void validateDatasource(DatasourceEntity entity, String passwordPlain) {
        if (entity.getName() == null || entity.getName().isBlank()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (entity.getApiBaseUrl() == null || entity.getApiBaseUrl().isBlank()) {
            throw new IllegalArgumentException("API 基础地址不能为空");
        }
        if (!entity.getApiBaseUrl().startsWith("http://") && !entity.getApiBaseUrl().startsWith("https://")) {
            throw new IllegalArgumentException("API 基础地址必须以 http:// 或 https:// 开头");
        }
        ApiAuthType authType = ApiAuthType.fromString(entity.getAuthType());
        Map<String, String> authConfig = parseStringMap(entity.getAuthConfigJson());
        switch (authType) {
            case BEARER_TOKEN -> {
                if (!StringUtils.hasText(passwordPlain) && !StringUtils.hasText(entity.getPasswordCipher())) {
                    throw new IllegalArgumentException("Bearer Token 不能为空");
                }
            }
            case BASIC -> {
                if (!StringUtils.hasText(entity.getUsername())) {
                    throw new IllegalArgumentException("Basic 鉴权用户名不能为空");
                }
                if (!StringUtils.hasText(passwordPlain) && !StringUtils.hasText(entity.getPasswordCipher())) {
                    throw new IllegalArgumentException("Basic 鉴权密码不能为空");
                }
            }
            case API_KEY_HEADER, API_KEY_QUERY -> {
                String keyName = authConfig.get("keyName");
                if (!StringUtils.hasText(keyName)) {
                    throw new IllegalArgumentException("API Key 名称不能为空");
                }
                if (!StringUtils.hasText(passwordPlain) && !StringUtils.hasText(entity.getPasswordCipher())) {
                    throw new IllegalArgumentException("API Key 值不能为空");
                }
            }
            default -> {
            }
        }
        if (entity.getRequestTimeoutMs() == null || entity.getRequestTimeoutMs() < 500) {
            entity.setRequestTimeoutMs(5000);
        }
    }

    public void testConnection(DatasourceEntity entity, String passwordPlain) {
        String path = parseStringMap(entity.getConfigJson()).getOrDefault("healthcheckPath", "");
        log.info(
                "api testConnection start datasourceId={} datasourceName={} baseUrl={} authType={} timeoutMs={} presetKey={} healthcheckPath={}",
                entity.getId(),
                entity.getName(),
                entity.getApiBaseUrl(),
                entity.getAuthType(),
                entity.getRequestTimeoutMs(),
                entity.getApiPresetKey(),
                path);
        execute(entity, passwordPlain, "GET", path, List.of(), List.of(), null, Map.of());
        log.info(
                "api testConnection success datasourceId={} datasourceName={} baseUrl={} healthcheckPath={}",
                entity.getId(),
                entity.getName(),
                entity.getApiBaseUrl(),
                path);
    }

    public void ensureRequiredParams(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null || String.valueOf(entry.getValue()).isBlank()) {
                throw new IllegalArgumentException("Missing parameter: " + entry.getKey());
            }
        }
    }

    public ApiPreviewResponse preview(
            DatasourceEntity entity,
            String paramSchemaJson,
            ApiQueryConfig config,
            List<String> args
    ) {
        Map<String, Object> params = buildParamMap(args, paramSchemaJson);
        log.info(
                "api preview start datasourceId={} datasourceName={} method={} path={} resultPointer={} paramNames={} argCount={}",
                entity.getId(),
                entity.getName(),
                config.getMethod(),
                config.getPath(),
                config.getResponseRootPointer(),
                QueryParamSchema.parseParamNames(paramSchemaJson),
                args == null ? 0 : args.size());
        JsonNode node = execute(
                entity,
                null,
                config.getMethod(),
                config.getPath(),
                config.getQueryParams(),
                config.getHeaders(),
                config.getBodyTemplate(),
                params);
        JsonNode root = resolveRoot(node, config.getResponseRootPointer());
        List<Map<String, Object>> sampleRows = applyLocalResultLimit(toRows(root, config.getOutputs()), config, params);
        List<ApiPreviewResponse.DiscoveredField> fields = discoverFields(root);
        log.info(
                "api preview success datasourceId={} datasourceName={} fieldCount={} sampleRowCount={}",
                entity.getId(),
                entity.getName(),
                fields.size(),
                sampleRows.size());
        return ApiPreviewResponse.builder().fields(fields).sampleRows(sampleRows).build();
    }

    public List<Map<String, Object>> executeQuery(
            DatasourceEntity entity,
            String paramSchemaJson,
            ApiQueryConfig config,
            List<String> args
    ) {
        Map<String, Object> params = buildParamMap(args, paramSchemaJson);
        log.info(
                "api executeQuery start datasourceId={} datasourceName={} method={} path={} resultPointer={} paramNames={} argCount={} localLimitParam={} localLimitFixed={}",
                entity.getId(),
                entity.getName(),
                config.getMethod(),
                config.getPath(),
                config.getResponseRootPointer(),
                QueryParamSchema.parseParamNames(paramSchemaJson),
                args == null ? 0 : args.size(),
                config.getLocalResultLimitParamName(),
                config.getLocalResultLimit());
        JsonNode node = execute(
                entity,
                null,
                config.getMethod(),
                config.getPath(),
                config.getQueryParams(),
                config.getHeaders(),
                config.getBodyTemplate(),
                params);
        List<Map<String, Object>> rows = applyLocalResultLimit(toRows(resolveRoot(node, config.getResponseRootPointer()), config.getOutputs()), config, params);
        log.info(
                "api executeQuery success datasourceId={} datasourceName={} rowCount={} localLimitParam={} localLimitFixed={}",
                entity.getId(),
                entity.getName(),
                rows.size(),
                config.getLocalResultLimitParamName(),
                config.getLocalResultLimit());
        return rows;
    }

    List<Map<String, Object>> applyLocalResultLimit(
            List<Map<String, Object>> rows,
            ApiQueryConfig config,
            Map<String, Object> params
    ) {
        if (rows == null || rows.isEmpty() || config == null) {
            return rows;
        }
        Integer limit = config.getLocalResultLimit();
        if (config.getLocalResultLimitParamName() != null && !config.getLocalResultLimitParamName().isBlank()) {
            String name = config.getLocalResultLimitParamName().trim();
            Object raw = params.get(name);
            String text = raw == null ? "" : String.valueOf(raw).trim();
            if (!text.isEmpty()) {
                try {
                    limit = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("条数参数必须为正整数：" + name);
                }
            }
        }
        if (limit == null || limit <= 0 || rows.size() <= limit) {
            log.info(
                    "api local result limit skip configuredParam={} configuredFixed={} finalLimit={} originalRowCount={}",
                    config.getLocalResultLimitParamName(),
                    config.getLocalResultLimit(),
                    limit,
                    rows.size());
            return rows;
        }
        log.info(
                "api local result limit applied configuredParam={} configuredFixed={} finalLimit={} originalRowCount={} truncatedRowCount={}",
                config.getLocalResultLimitParamName(),
                config.getLocalResultLimit(),
                limit,
                rows.size(),
                limit);
        return new ArrayList<>(rows.subList(0, limit));
    }

    private JsonNode execute(
            DatasourceEntity entity,
            String passwordPlain,
            String method,
            String rawPath,
            List<ApiQueryConfig.ApiRequestValue> queryParams,
            List<ApiQueryConfig.ApiRequestValue> headers,
            String bodyTemplate,
            Map<String, Object> params
    ) {
        String resolvedPath = applyTemplate(rawPath, params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(entity.getApiBaseUrl().trim());
        if (StringUtils.hasText(resolvedPath)) {
            if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
                builder = UriComponentsBuilder.fromHttpUrl(resolvedPath);
            } else {
                String pathPart = resolvedPath;
                String queryPart = null;
                int queryIndex = resolvedPath.indexOf('?');
                if (queryIndex >= 0) {
                    pathPart = resolvedPath.substring(0, queryIndex);
                    queryPart = resolvedPath.substring(queryIndex + 1);
                }
                if (StringUtils.hasText(pathPart)) {
                    builder.path(pathPart.startsWith("/") ? pathPart : "/" + pathPart);
                }
                if (StringUtils.hasText(queryPart)) {
                    builder.query(queryPart);
                }
            }
        }
        for (Map.Entry<String, String> entry : parseStringMap(entity.getDefaultQueryParamsJson()).entrySet()) {
            builder.queryParam(entry.getKey(), applyTemplate(entry.getValue(), params));
        }
        for (ApiQueryConfig.ApiRequestValue item : queryParams) {
            if (item.getKey() == null || item.getKey().isBlank()) {
                continue;
            }
            builder.queryParam(item.getKey(), resolveRequestValue(item, params));
        }

        String httpMethod = method == null ? "GET" : method.trim().toUpperCase(Locale.ROOT);
        applyAuth(entity, passwordPlain, builder, defaultHeaders(entity, params), params);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = entity.getRequestTimeoutMs() != null ? entity.getRequestTimeoutMs() : 5000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        RestClient client = restClientBuilder.clone().requestFactory(factory).build();
        UriComponents uriComponents = builder.build(false).encode(StandardCharsets.UTF_8);
        URI targetUri = uriComponents.toUri();
        Map<String, String> defaultHeaders = defaultHeaders(entity, params);
        int customHeaderCount = headers == null ? 0 : (int) headers.stream().filter(item -> item != null && StringUtils.hasText(item.getKey())).count();
        int queryParamCount = queryParams == null ? 0 : (int) queryParams.stream().filter(item -> item != null && StringUtils.hasText(item.getKey())).count();
        log.info(
                "api request prepared datasourceId={} datasourceName={} method={} target={} authType={} defaultHeaderCount={} customHeaderCount={} customQueryCount={} hasBodyTemplate={} timeoutMs={}",
                entity.getId(),
                entity.getName(),
                httpMethod,
                sanitizeUriForLog(targetUri),
                entity.getAuthType(),
                defaultHeaders.size(),
                customHeaderCount,
                queryParamCount,
                StringUtils.hasText(bodyTemplate),
                timeoutMs);
        byte[] rawBytes;
        try {
            if ("POST".equals(httpMethod) || "PUT".equals(httpMethod)) {
                RestClient.RequestBodySpec request = ("POST".equals(httpMethod) ? client.post() : client.put())
                        .uri(targetUri);
                request.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                    request.header(entry.getKey(), entry.getValue());
                }
                for (ApiQueryConfig.ApiRequestValue item : headers) {
                    if (item.getKey() == null || item.getKey().isBlank()) {
                        continue;
                    }
                    request.header(item.getKey(), resolveRequestValue(item, params));
                }
                if (StringUtils.hasText(bodyTemplate)) {
                    request.contentType(MediaType.APPLICATION_JSON).body(applyTemplate(bodyTemplate, params));
                }
                rawBytes = request.retrieve().body(byte[].class);
            } else {
                RestClient.RequestHeadersSpec<?> request = client.get().uri(targetUri);
                request.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                    request.header(entry.getKey(), entry.getValue());
                }
                for (ApiQueryConfig.ApiRequestValue item : headers) {
                    if (item.getKey() == null || item.getKey().isBlank()) {
                        continue;
                    }
                    request.header(item.getKey(), resolveRequestValue(item, params));
                }
                rawBytes = request.retrieve().body(byte[].class);
            }
        } catch (RestClientResponseException e) {
            String target = uriComponents.toUriString();
            log.warn(
                    "api request upstream http error datasourceId={} datasourceName={} method={} target={} status={} responseBodyPrefix={}",
                    entity.getId(),
                    entity.getName(),
                    httpMethod,
                    sanitizeUriForLog(targetUri),
                    e.getStatusCode().value(),
                    abbreviate(e.getResponseBodyAsString(), 240));
            throw new IllegalArgumentException(
                    "外部 API 返回 " + e.getStatusCode().value() +
                            "，请检查基础地址、模板和健康检查路径是否匹配：" + target);
        } catch (ResourceAccessException e) {
            String target = uriComponents.toUriString();
            log.warn(
                    "api request network error datasourceId={} datasourceName={} method={} target={} error={}",
                    entity.getId(),
                    entity.getName(),
                    httpMethod,
                    sanitizeUriForLog(targetUri),
                    abbreviate(e.getMessage(), 240));
            throw new IllegalArgumentException(
                    "连接外部 API 失败（网络超时或目标站点不可达）。请检查本机网络、代理设置，或更换可访问的数据源模板：" + target);
        }
        String raw = rawBytes == null ? "" : new String(rawBytes, StandardCharsets.UTF_8);
        log.info(
                "api request success datasourceId={} datasourceName={} method={} target={} responseBytes={}",
                entity.getId(),
                entity.getName(),
                httpMethod,
                sanitizeUriForLog(targetUri),
                rawBytes == null ? 0 : rawBytes.length);
        try {
            return MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            log.warn(
                    "api request invalid json datasourceId={} datasourceName={} method={} target={} responsePrefix={}",
                    entity.getId(),
                    entity.getName(),
                    httpMethod,
                    sanitizeUriForLog(targetUri),
                    abbreviate(raw, 240));
            throw new IllegalArgumentException("API 未返回有效 JSON");
        }
    }

    private void applyAuth(
            DatasourceEntity entity,
            String passwordPlain,
            UriComponentsBuilder uriBuilder,
            Map<String, String> defaultHeaders,
            Map<String, Object> params
    ) {
        String secret = StringUtils.hasText(passwordPlain)
                ? passwordPlain
                : StringUtils.hasText(entity.getPasswordCipher()) ? encryptionService.decrypt(entity.getPasswordCipher()) : "";
        Map<String, String> authConfig = parseStringMap(entity.getAuthConfigJson());
        ApiAuthType authType = ApiAuthType.fromString(entity.getAuthType());
        switch (authType) {
            case BEARER_TOKEN -> defaultHeaders.put(HttpHeaders.AUTHORIZATION, "Bearer " + secret);
            case BASIC -> {
                String source = entity.getUsername() + ":" + secret;
                String encoded = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
                defaultHeaders.put(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            }
            case API_KEY_HEADER -> {
                String keyName = authConfig.getOrDefault("keyName", "X-API-Key");
                defaultHeaders.put(keyName, secret);
            }
            case API_KEY_QUERY -> {
                String keyName = authConfig.getOrDefault("keyName", "api_key");
                uriBuilder.queryParam(keyName, applyTemplate(secret, params));
            }
            default -> {
            }
        }
    }

    private Map<String, String> defaultHeaders(DatasourceEntity entity, Map<String, Object> params) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parseStringMap(entity.getDefaultHeadersJson()).entrySet()) {
            out.put(entry.getKey(), applyTemplate(entry.getValue(), params));
        }
        return out;
    }

    private JsonNode resolveRoot(JsonNode node, String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return node;
        }
        JsonNode resolved = node.at(pointer.trim());
        if (resolved.isMissingNode() || resolved.isNull()) {
            throw new IllegalArgumentException("响应中未找到指定结果位置");
        }
        return resolved;
    }

    private List<Map<String, Object>> toRows(JsonNode root, List<ApiQueryConfig.ApiOutputField> outputs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                rows.add(toRow(item, outputs));
            }
            return rows;
        }
        rows.add(toRow(root, outputs));
        return rows;
    }

    private Map<String, Object> toRow(JsonNode node, List<ApiQueryConfig.ApiOutputField> outputs) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (outputs == null || outputs.isEmpty()) {
            discoverLeafNodes(node, "", row);
            return row;
        }
        for (ApiQueryConfig.ApiOutputField output : outputs) {
            JsonNode fieldNode = node.at(output.getJsonPointer());
            row.put(output.getKey(), toDisplayValue(fieldNode));
        }
        return row;
    }

    private List<ApiPreviewResponse.DiscoveredField> discoverFields(JsonNode root) {
        Map<String, Object> flat = new LinkedHashMap<>();
        JsonNode sample = root.isArray() ? root.path(0) : root;
        discoverLeafNodes(sample, "", flat);
        List<ApiPreviewResponse.DiscoveredField> out = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String pointer = entry.getKey();
            String key = pointer.replace('/', '.').replace("[", "_").replace("]", "");
            if (key.startsWith(".")) {
                key = key.substring(1);
            }
            if (key.isBlank()) {
                key = "field_" + index;
            }
            out.add(ApiPreviewResponse.DiscoveredField.builder()
                    .key(key)
                    .label(humanizeKey(key))
                    .jsonPointer(pointer)
                    .sampleValue(entry.getValue() == null ? "" : String.valueOf(entry.getValue()))
                    .build());
            index++;
        }
        return out;
    }

    private void discoverLeafNodes(JsonNode node, String pointer, Map<String, Object> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            out.put(pointer.isBlank() ? "/" : pointer, "");
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> discoverLeafNodes(node.path(entry.getKey()), pointer + "/" + entry.getKey(), out));
            return;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                out.put(pointer.isBlank() ? "/" : pointer, "[]");
                return;
            }
            discoverLeafNodes(node.path(0), pointer + "/0", out);
            return;
        }
        out.put(pointer.isBlank() ? "/" : pointer, toDisplayValue(node));
    }

    private String resolveRequestValue(ApiQueryConfig.ApiRequestValue item, Map<String, Object> params) {
        if (item == null) {
            return "";
        }
        if ("PARAM".equalsIgnoreCase(item.getValueSource())) {
            Object value = params.get(item.getParamName());
            String resolved = value == null ? "" : String.valueOf(value).trim();
            if (!resolved.isEmpty()) {
                return resolved;
            }
            // fallback to template sample value (e.g. limit=10) when runtime arg is missing/blank
            String sample = item.getSampleValue() == null ? "" : item.getSampleValue().trim();
            return sample;
        }
        return applyTemplate(item.getValue(), params);
    }

    private String applyTemplate(String raw, Map<String, Object> params) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String out = raw;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String val = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            out = out.replace("{{" + entry.getKey() + "}}", val);
        }
        return out;
    }

    private String toDisplayValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isNumber()) {
            // Keep numeric samples human-friendly in preview (avoid scientific notation like 2.0E7).
            BigDecimal decimal = node.decimalValue();
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private Map<String, Object> buildParamMap(List<String> args, String paramSchemaJson) {
        List<String> names = QueryParamSchema.parseParamNames(paramSchemaJson);
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            params.put(names.get(i), i < args.size() ? args.get(i) : "");
        }
        return params;
    }

    private Map<String, String> parseStringMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> data = MAPPER.readValue(rawJson, new TypeReference<Map<String, String>>() {});
            return data == null ? Map.of() : data;
        } catch (Exception ex) {
            throw new IllegalArgumentException("配置 JSON 格式不正确");
        }
    }

    private String humanizeKey(String key) {
        String normalized = key.replace('.', ' ').replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return "字段";
        }
        return normalized;
    }

    private String sanitizeUriForLog(URI uri) {
        if (uri == null) {
            return "-";
        }
        String query = uri.getQuery();
        if (!StringUtils.hasText(query)) {
            return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        }
        List<String> keys = new ArrayList<>();
        for (String part : query.split("&")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            int idx = part.indexOf('=');
            keys.add(idx >= 0 ? part.substring(0, idx) : part);
        }
        return uri.getScheme() + "://" + uri.getHost() + uri.getPath() + "?keys=" + String.join(",", keys);
    }

    private String abbreviate(String raw, int maxLen) {
        if (raw == null) {
            return "";
        }
        String compact = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= maxLen ? compact : compact.substring(0, maxLen) + "...";
    }
}
