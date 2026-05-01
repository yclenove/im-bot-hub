package com.sov.imhub.domain;

public enum DatasourceType {
    DATABASE,
    API;

    public static DatasourceType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return DATABASE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DATABASE;
        }
    }
}
