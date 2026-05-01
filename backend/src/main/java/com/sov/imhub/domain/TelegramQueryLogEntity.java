package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_telegram_query_log")
public class TelegramQueryLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime createdAt;
    private Long botId;
    /** TELEGRAM | LARK 等；NULL 表示历史行（视为 Telegram） */
    private String imPlatform;
    private Long telegramUserId;
    private Long chatId;
    /** 非 Telegram 时的用户标识（如飞书 open_id） */
    private String externalUserId;
    /** 非 Telegram 时的会话标识 */
    private String externalChatId;
    private String command;
    private Long queryDefinitionId;
    private Boolean success;
    private String errorKind;
    private Integer durationMs;
    private String detail;
}
