package com.sov.imhub.web.admin;

import com.sov.imhub.admin.dto.QueryTemplateResponse;
import com.sov.imhub.service.QueryTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 查询模板市场 API。
 */
@RestController
@RequestMapping("/api/admin/templates")
@RequiredArgsConstructor
public class AdminQueryTemplateController {

    private final QueryTemplateService templateService;

    /**
     * 获取模板列表。
     */
    @GetMapping
    public List<QueryTemplateResponse> list(@RequestParam(required = false) String category) {
        return templateService.listEnabled(category);
    }

    /**
     * 获取单个模板。
     */
    @GetMapping("/{templateId}")
    public QueryTemplateResponse get(@PathVariable Long templateId) {
        return templateService.getById(templateId);
    }

    /**
     * 一键导入模板到机器人。
     */
    @PostMapping("/{templateId}/import")
    public Map<String, Object> importTemplate(
            @PathVariable Long templateId,
            @RequestBody Map<String, Long> body) {
        Long botId = body.get("botId");
        Long datasourceId = body.get("datasourceId");
        if (botId == null || datasourceId == null) {
            throw new IllegalArgumentException("botId 和 datasourceId 不能为空");
        }
        Long queryId = templateService.importTemplate(templateId, botId, datasourceId);
        return Map.of("success", true, "queryId", queryId);
    }

    /**
     * 创建自定义模板。
     */
    @PostMapping
    public QueryTemplateResponse create(@RequestBody Map<String, String> body) {
        return templateService.create(
                body.get("name"),
                body.get("category"),
                body.get("description"),
                body.get("configJson"),
                body.get("author"));
    }
}
