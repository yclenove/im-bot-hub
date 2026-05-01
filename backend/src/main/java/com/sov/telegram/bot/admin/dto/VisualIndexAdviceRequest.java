package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VisualIndexAdviceRequest {
    @NotBlank
    private String visualConfigJson;
}
