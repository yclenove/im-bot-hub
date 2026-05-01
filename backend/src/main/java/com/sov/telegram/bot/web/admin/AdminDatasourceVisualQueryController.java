package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.VisualBenchmarkRequest;
import com.sov.telegram.bot.admin.dto.VisualBenchmarkResponse;
import com.sov.telegram.bot.admin.dto.VisualIndexAdviceRequest;
import com.sov.telegram.bot.admin.dto.VisualIndexAdviceResponse;
import com.sov.telegram.bot.service.visual.VisualQueryBenchmarkService;
import com.sov.telegram.bot.service.visual.VisualQueryIndexAdviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/datasources/{datasourceId}/visual-query")
@RequiredArgsConstructor
@Validated
public class AdminDatasourceVisualQueryController {

    private final VisualQueryBenchmarkService visualQueryBenchmarkService;
    private final VisualQueryIndexAdviceService visualQueryIndexAdviceService;

    @PostMapping("/benchmark")
    public VisualBenchmarkResponse benchmark(
            @PathVariable long datasourceId, @Valid @RequestBody VisualBenchmarkRequest req) {
        return visualQueryBenchmarkService.run(datasourceId, req);
    }

    @PostMapping("/index-advice")
    public VisualIndexAdviceResponse indexAdvice(
            @PathVariable long datasourceId, @Valid @RequestBody VisualIndexAdviceRequest req) {
        return visualQueryIndexAdviceService.advise(datasourceId, req.getVisualConfigJson());
    }
}
