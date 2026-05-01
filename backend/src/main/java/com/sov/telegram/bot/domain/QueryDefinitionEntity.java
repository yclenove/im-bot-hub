package com.sov.telegram.bot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_query_definition")
public class QueryDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long botId;
    private Long datasourceId;
    private String command;
    /** 管理列表与 Telegram 菜单副标题优先；可为空 */
    private String name;
    /** 非空时整条 setMyCommands description 使用该值（截断 255），不再自动拼接示例后缀 */
    private String telegramMenuDescription;
    private String sqlTemplate;
    /** {@link QueryMode} name: SQL or VISUAL */
    private String queryMode;
    /** Wizard JSON when queryMode is VISUAL; optional echo when SQL */
    private String visualConfigJson;
    /** API query configuration JSON when queryMode is API */
    private String apiConfigJson;
    private String paramSchemaJson;
    private Integer timeoutMs;
    private Integer maxRows;
    private Boolean enabled;
    @TableLogic
    private Integer deleted;
    private Long deleteToken;
    /** LIST | LIST_DOT | LIST_CODE | LIST_BLOCKQUOTE | SECTION | MONO_PRE | CODE_BLOCK | KV_SINGLE_LINE — 见 FieldRenderService */
    private String telegramReplyStyle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
