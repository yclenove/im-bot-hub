package com.sov.telegram.bot.service.api;

import lombok.Data;

import java.util.List;

@Data
public class ApiQueryPreviewRequest {
    private String apiConfigJson;
    private List<String> args;
}
