package com.sov.telegram.bot.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.telegram.bot.admin.dto.FieldMappingResponse;
import com.sov.telegram.bot.admin.dto.FieldMappingUpsertRequest;
import com.sov.telegram.bot.domain.FieldMappingEntity;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import com.sov.telegram.bot.mapstruct.AdminDtoMapper;
import com.sov.telegram.bot.mapper.FieldMappingMapper;
import com.sov.telegram.bot.mapper.QueryDefinitionMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/queries/{queryId}/fields")
@RequiredArgsConstructor
public class AdminFieldMappingController {

    private final FieldMappingMapper fieldMappingMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final AdminDtoMapper adminDtoMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<FieldMappingResponse> list(@PathVariable Long queryId) {
        ensureQueryExists(queryId);
        return fieldMappingMapper
                .selectList(new LambdaQueryWrapper<FieldMappingEntity>().eq(FieldMappingEntity::getQueryId, queryId))
                .stream()
                .map(adminDtoMapper::toFieldMappingResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    public FieldMappingResponse create(@PathVariable Long queryId, @Valid @RequestBody FieldMappingUpsertRequest req) {
        ensureQueryExists(queryId);
        FieldMappingEntity e = new FieldMappingEntity();
        e.setQueryId(queryId);
        e.setColumnName(req.getColumnName());
        e.setLabel(req.getLabel());
        e.setSortOrder(req.getSortOrder());
        e.setMaskType(req.getMaskType());
        e.setFormatType(req.getFormatType());
        e.setDisplayPipelineJson(req.getDisplayPipelineJson());
        fieldMappingMapper.insert(e);
        auditLogService.log("CREATE", "FIELD_MAPPING", String.valueOf(e.getId()), req.getColumnName());
        return adminDtoMapper.toFieldMappingResponse(fieldMappingMapper.selectById(e.getId()));
    }

    @PutMapping("/{fieldId}")
    public FieldMappingResponse update(
            @PathVariable Long queryId, @PathVariable Long fieldId, @Valid @RequestBody FieldMappingUpsertRequest req) {
        FieldMappingEntity e = fieldMappingMapper.selectById(fieldId);
        if (e == null || !queryId.equals(e.getQueryId())) {
            throw new NotFoundException("field mapping not found");
        }
        e.setColumnName(req.getColumnName());
        e.setLabel(req.getLabel());
        e.setSortOrder(req.getSortOrder());
        e.setMaskType(req.getMaskType());
        e.setFormatType(req.getFormatType());
        e.setDisplayPipelineJson(req.getDisplayPipelineJson());
        fieldMappingMapper.updateById(e);
        auditLogService.log("UPDATE", "FIELD_MAPPING", String.valueOf(fieldId), req.getColumnName());
        return adminDtoMapper.toFieldMappingResponse(fieldMappingMapper.selectById(fieldId));
    }

    @DeleteMapping("/{fieldId}")
    public void delete(@PathVariable Long queryId, @PathVariable Long fieldId) {
        FieldMappingEntity e = fieldMappingMapper.selectById(fieldId);
        if (e == null || !queryId.equals(e.getQueryId())) {
            throw new NotFoundException("field mapping not found");
        }
        fieldMappingMapper.deleteById(fieldId);
        auditLogService.log("DELETE", "FIELD_MAPPING", String.valueOf(fieldId), null);
    }

    private void ensureQueryExists(Long queryId) {
        QueryDefinitionEntity q = queryDefinitionMapper.selectById(queryId);
        if (q == null) {
            throw new NotFoundException("query not found");
        }
    }
}
