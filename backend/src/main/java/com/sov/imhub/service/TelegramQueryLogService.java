package com.sov.imhub.service;

import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.domain.TelegramQueryLogEntity;
import com.sov.imhub.mapper.TelegramQueryLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 记录 Telegram 用户发起的斜杠命令处理结果；不落 Token、不落业务参数值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramQueryLogService {

    static final int DETAIL_MAX = 500;

    public static final String KIND_SUCCESS = "SUCCESS";
    public static final String KIND_HELP = "HELP";
    public static final String KIND_RATE_LIMIT = "RATE_LIMIT";
    public static final String KIND_NOT_ALLOWED = "NOT_ALLOWED";
    public static final String KIND_UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String KIND_MISSING_PARAM = "MISSING_PARAM";
    public static final String KIND_QUERY_FAILED = "QUERY_FAILED";

    /** 管理端筛选 {@code errorKind} 时仅允许这些取值（与写入侧一致）。 */
    public static final Set<String> ERROR_KINDS =
            Set.of(
                    KIND_SUCCESS,
                    KIND_HELP,
                    KIND_RATE_LIMIT,
                    KIND_NOT_ALLOWED,
                    KIND_UNKNOWN_COMMAND,
                    KIND_MISSING_PARAM,
                    KIND_QUERY_FAILED);

    private final TelegramQueryLogMapper telegramQueryLogMapper;

    public void insertSafe(
            long botId,
            long telegramUserId,
            long chatId,
            String command,
            Long queryDefinitionId,
            boolean success,
            String errorKind,
            Long startedAtMs,
            String detail
    ) {
        insertImSafe(
                botId,
                ImPlatform.TELEGRAM,
                telegramUserId,
                chatId,
                null,
                null,
                command,
                queryDefinitionId,
                success,
                errorKind,
                startedAtMs,
                detail);
    }

    public void insertImSafe(
            long botId,
            ImPlatform platform,
            long telegramUserId,
            long telegramChatId,
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
            TelegramQueryLogEntity e = new TelegramQueryLogEntity();
            e.setCreatedAt(LocalDateTime.now());
            e.setBotId(botId);
            e.setImPlatform(platform == ImPlatform.TELEGRAM ? null : platform.wireName());
            e.setTelegramUserId(telegramUserId);
            e.setChatId(telegramChatId);
            e.setExternalUserId(truncateNullable(externalUserId, 128));
            e.setExternalChatId(truncateNullable(externalChatId, 128));
            e.setCommand(truncate(command, 64));
            e.setQueryDefinitionId(queryDefinitionId);
            e.setSuccess(success);
            e.setErrorKind(truncate(errorKind, 32));
            if (startedAtMs != null) {
                long d = System.currentTimeMillis() - startedAtMs;
                e.setDurationMs((int) Math.min(Math.max(d, 0), Integer.MAX_VALUE));
            }
            e.setDetail(truncate(detail, DETAIL_MAX));
            telegramQueryLogMapper.insert(e);
        } catch (Exception ex) {
            log.debug("telegram query log insert skipped: {}", ex.toString());
        }
    }

    private static String truncateNullable(String s, int max) {
        if (s == null) {
            return null;
        }
        return truncate(s, max);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    /** 仅参数名列表，不含值 */
    public static String paramNamesOnly(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        return "params=" + String.join(",", names);
    }
}
