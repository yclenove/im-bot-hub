package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VisualBenchmarkRequest {

    @NotBlank
    private String visualConfigJson;

    @NotNull
    private Integer maxRows;

    @NotNull
    private Integer timeoutMs;

    /** Positional args matching param_schema order (same as test query). */
    private List<String> args = new ArrayList<>();
}
