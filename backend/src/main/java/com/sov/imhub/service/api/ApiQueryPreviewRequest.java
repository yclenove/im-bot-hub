package com.sov.imhub.service.api;

import lombok.Data;

import java.util.List;

@Data
public class ApiQueryPreviewRequest {
    private String apiConfigJson;
    private List<String> args;
}
