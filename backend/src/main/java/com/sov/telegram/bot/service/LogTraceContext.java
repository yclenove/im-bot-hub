package com.sov.telegram.bot.service;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一封装 MDC 追踪字段，便于把 webhook / dispatch / query execution 串成一条日志链路。
 */
public final class LogTraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String UPDATE_ID = "updateId";
    public static final String QUERY_ID = "queryId";
    public static final String COMMAND = "command";

    private LogTraceContext() {
    }

    public static Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(TRACE_ID, MDC.get(TRACE_ID));
        out.put(UPDATE_ID, MDC.get(UPDATE_ID));
        out.put(QUERY_ID, MDC.get(QUERY_ID));
        out.put(COMMAND, MDC.get(COMMAND));
        return out;
    }

    public static void restore(Map<String, String> snapshot) {
        putOrRemove(TRACE_ID, snapshot.get(TRACE_ID));
        putOrRemove(UPDATE_ID, snapshot.get(UPDATE_ID));
        putOrRemove(QUERY_ID, snapshot.get(QUERY_ID));
        putOrRemove(COMMAND, snapshot.get(COMMAND));
    }

    public static void putTrace(String traceId, String updateId) {
        putOrRemove(TRACE_ID, traceId);
        putOrRemove(UPDATE_ID, updateId);
    }

    public static void putCommand(String command) {
        putOrRemove(COMMAND, command);
    }

    public static void putQueryId(Long queryId) {
        putOrRemove(QUERY_ID, queryId == null ? null : String.valueOf(queryId));
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}
