package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.service.QueryParamSchema;
import com.sov.telegram.bot.service.api.ApiDatasourceSupport;
import com.sov.telegram.bot.service.api.ApiPreviewResponse;
import com.sov.telegram.bot.service.api.ApiQueryConfig;
import com.sov.telegram.bot.service.api.ApiQueryConfigService;
import com.sov.telegram.bot.service.api.ApiQueryPreviewRequest;
import com.sov.telegram.bot.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/datasources/{datasourceId}/api-query")
@RequiredArgsConstructor
public class AdminDatasourceApiQueryController {

    private final DatasourceMapper datasourceMapper;
    private final ApiQueryConfigService apiQueryConfigService;
    private final ApiDatasourceSupport apiDatasourceSupport;

    @PostMapping("/preview")
    public ApiPreviewResponse preview(
            @PathVariable Long datasourceId,
            @RequestBody ApiQueryPreviewRequest req) {
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new NotFoundException("datasource not found");
        }
        ApiQueryConfig config = apiQueryConfigService.parseConfig(req.getApiConfigJson());
        List<String> args = req.getArgs() != null ? req.getArgs() : List.of();
        return apiDatasourceSupport.preview(datasource, inferParamSchema(config), config, args);
    }

    private String inferParamSchema(ApiQueryConfig config) {
        List<String> params = new ArrayList<>();
        collectParams(config.getQueryParams(), params);
        collectParams(config.getHeaders(), params);
        if (config.getBodyTemplate() != null) {
            collectTemplateParams(config.getBodyTemplate(), params);
        }
        collectTemplateParams(config.getPath(), params);
        if (config.getLocalResultLimitParamName() != null && !config.getLocalResultLimitParamName().isBlank()) {
            String limitParam = config.getLocalResultLimitParamName().trim();
            if (!params.contains(limitParam)) {
                params.add(limitParam);
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("params", params);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{\"params\":[]}";
        }
    }

    private void collectParams(List<ApiQueryConfig.ApiRequestValue> values, List<String> params) {
        if (values == null) {
            return;
        }
        for (ApiQueryConfig.ApiRequestValue value : values) {
            if (value == null) {
                continue;
            }
            if ("PARAM".equalsIgnoreCase(value.getValueSource()) && value.getParamName() != null && !value.getParamName().isBlank()) {
                if (!params.contains(value.getParamName().trim())) {
                    params.add(value.getParamName().trim());
                }
            }
            collectTemplateParams(value.getValue(), params);
        }
    }

    private void collectTemplateParams(String text, List<String> params) {
        if (text == null || text.isBlank()) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{(\\w+)}}")
                .matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!params.contains(name)) {
                params.add(name);
            }
        }
    }
}
