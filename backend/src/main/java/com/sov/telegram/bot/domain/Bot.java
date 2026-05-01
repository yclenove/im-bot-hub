package com.sov.telegram.bot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_bot")
public class Bot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String telegramBotToken;
    private String telegramBotUsername;
    /** If non-null/non-blank, POST /api/webhook must send X-Telegram-Bot-Api-Secret-Token with same value. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String webhookSecretToken;
    private Boolean enabled;
    /** ALL = 任意会话；GROUPS_ONLY = 仅允许 listed 群（见下），私聊一律忽略 */
    private String telegramChatScope;
    /** JSON 数组，Telegram 群/超级群 chat_id，如 [-1001234567890] */
    private String telegramAllowedChatIdsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
