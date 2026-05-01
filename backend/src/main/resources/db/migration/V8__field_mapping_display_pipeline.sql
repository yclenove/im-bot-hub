-- 字段展现流水线（JSON 数组，见 DisplayPipelineApplier / FieldRenderService）
ALTER TABLE t_field_mapping
    ADD COLUMN display_pipeline_json TEXT NULL
        COMMENT 'JSON array of {op,...} display transforms' AFTER format_type;
