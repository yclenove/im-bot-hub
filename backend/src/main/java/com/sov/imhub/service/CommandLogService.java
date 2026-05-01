package com.sov.imhub.service;

import com.sov.imhub.domain.CommandLogEntity;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.mapper.CommandLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 通用命令日志服务：替代 TelegramQueryLogService，全平台统一记录命令执行结果。
 * 不记录 Token、业务参数值等敏感信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandLogService {

    static final int DETAIL_MAX = 500;

    public static final String KIND_SUCCESS = "SUCCESS";
    public static final String KIND_HELP = "HELP";
    public static final String KIND_RATE_LIMIT = "RATE_LIMIT";
    public static final String KIND_NOT_ALLOWED = "NOT_ALLOWED";
    public static final String KIND_UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String KIND_MISSING_PARAM = "MISSING_PARAM";
    public static final String KIND_QUERY_FAILED = "QUERY_FAILED";

    /** 管理端筛选 errorKind 时仅允许这些取值。 */
    public static final Set<String> ERROR_KINDS =
            Set.of(
                    KIND_SUCCESS,
                    KIND_HELP,
                    KIND_RATE_LIMIT,
                    KIND_NOT_ALLOWED,
                    KIND_UNKNOWN_COMMAND,
                    KIND_MISSING_PARAM,
                    KIND_QUERY_FAILED);

    private final CommandLogMapper commandLogMapper;

    public void log(
            long botId,
            Long channelId,
            ImPlatform platform,
            String externalUserId,
            String externalChatId,
            String command,
            Long queryDefinitionId,
            boolean success,
            String errorKind,
            Long startedAtMs,
            String detail
    ) {
        try {
            CommandLogEntity e = new CommandLogEntity();
            e.setCreatedAt(LocalDateTime.now());
            e.setBotId(botId);
            e.setChannelId(channelId);
            e.setPlatform(platform == null ? "UNKNOWN" : platform.wireName());
            e.setExternalUserId(truncateNullable(externalUserId, 256));
            e.setExternalChatId(truncateNullable(externalChatId, 256));
            e.setCommand(truncate(command, 64));
            e.setQueryDefinitionId(queryDefinitionId);
            e.setSuccess(success);
            e.setErrorKind(truncate(errorKind, 32));
            if (startedAtMs != null) {
                long d = System.currentTimeMillis() - startedAtMs;
                e.setDurationMs((int) Math.min(Math.max(d, 0), Integer.MAX_VALUE));
            }
            e.setDetail(truncate(detail, DETAIL_MAX));
            commandLogMapper.insert(e);
        } catch (Exception ex) {
            log.debug("command log insert skipped: {}", ex.toString());
        }
    }

    private static String truncateNullable(String s, int max) {
        if (s == null) return null;
        return truncate(s, max);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    /** 仅参数名列表，不含值 */
    public static String paramNamesOnly(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        return "params=" + String.join(",", names);
    }
}
