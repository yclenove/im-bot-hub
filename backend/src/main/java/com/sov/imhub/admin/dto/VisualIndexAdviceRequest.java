package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VisualIndexAdviceRequest {
    @NotBlank
    private String visualConfigJson;
}
