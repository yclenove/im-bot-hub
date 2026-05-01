package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_field_mapping")
public class FieldMappingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long queryId;
    private String columnName;
    private String label;
    private Integer sortOrder;
    private String maskType;
    private String formatType;
    /** JSON array of display transform steps; see DisplayPipelineApplier */
    private String displayPipelineJson;
}
