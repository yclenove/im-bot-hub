package com.sov.imhub.web.admin;

import com.sov.imhub.ai.AnomalyDetectionService;
import com.sov.imhub.ai.Nl2SqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 功能 API。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final Nl2SqlService nl2SqlService;
    private final AnomalyDetectionService anomalyDetectionService;

    /**
     * NL2SQL：自然语言转 SQL。
     */
    @PostMapping("/nl2sql")
    public Nl2SqlService.Nl2SqlResult nl2sql(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        Long datasourceId = ((Number) body.get("datasourceId")).longValue();
        Long botId = ((Number) body.get("botId")).longValue();
        return nl2SqlService.convert(question, datasourceId, botId);
    }

    /**
     * 执行 NL2SQL 生成的查询。
     */
    @PostMapping("/nl2sql/execute")
    public List<Map<String, Object>> executeNl2sql(@RequestBody Map<String, Object> body) {
        Nl2SqlService.Nl2SqlResult result = new Nl2SqlService.Nl2SqlResult();
        result.setGeneratedSql((String) body.get("sql"));
        result.setDatasourceId(((Number) body.get("datasourceId")).longValue());
        List<Object> params = (List<Object>) body.get("params");
        return nl2SqlService.execute(result, params);
    }

    /**
     * 获取异常检测历史。
     */
    @GetMapping("/anomalies")
    public List<Map<String, Object>> getAnomalyHistory(
            @RequestParam(defaultValue = "20") int limit) {
        return anomalyDetectionService.getAnomalyHistory(limit);
    }

    /**
     * 手动触发异常检测。
     */
    @PostMapping("/anomalies/detect")
    public Map<String, Object> triggerAnomalyDetection() {
        anomalyDetectionService.runDetection();
        return Map.of("success", true, "message", "异常检测已触发");
    }
}
