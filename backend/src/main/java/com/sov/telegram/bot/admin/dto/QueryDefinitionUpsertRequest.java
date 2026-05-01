package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QueryDefinitionUpsertRequest {

    @NotNull
    private Long datasourceId;

    @NotBlank
    private String command;

    @Size(max = 128)
    private String name;

    /** 覆盖整条 Telegram 菜单描述（≤255）；空白表示自动生成 */
    @Size(max = 255)
    private String telegramMenuDescription;

    /**
     * Hand-written template when {@code queryMode} is {@code SQL}. Ignored when {@code VISUAL} (server generates).
     */
    private String sqlTemplate;

    /** {@code SQL} or {@code VISUAL}; default SQL */
    private String queryMode = "SQL";

    /** Wizard state JSON when {@code queryMode} is {@code VISUAL} */
    private String visualConfigJson;

    /** API query builder JSON when {@code queryMode} is {@code API} */
    private String apiConfigJson;

    /** JSON: {"params":["orderNo"]} — ignored for VISUAL (server generates from visual config) */
    private String paramSchemaJson;

    private int timeoutMs = 5000;
    private int maxRows = 1;
    private boolean enabled = true;

    /**
     * Telegram HTML 展现：LIST 默认；LIST_DOT 中间点分隔；LIST_CODE 数值用 {@code &lt;code&gt;}；LIST_BLOCKQUOTE
     * 每字段引用块；SECTION 分块；MONO_PRE 整块等宽（label: value）；CODE_BLOCK 整块等宽（label=value）；KV_SINGLE_LINE
     * 单行分号连接；VALUES_JOIN_SPACE/VALUES_JOIN_PIPE 每行仅拼接值；VALUES_JOIN_CUSTOM:分隔符 支持自定义连接符；TABLE_PRE
     * 以 key 为表头输出整块表格。
     */
    private String telegramReplyStyle = "LIST";
}
