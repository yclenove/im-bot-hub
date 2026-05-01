package com.sov.telegram.bot.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class TestQueryRequest {
    private List<String> args;

    /**
     * 仅 {@code VISUAL} 查询：{@code LEGACY_OR} 或 {@code UNION_ALL} 时从向导 JSON 临时重编译 SQL 再执行；
     * 为空则使用库内已保存的 {@code sql_template}（与 Telegram 一致）。
     */
    private String orCompositionStrategy;

    /**
     * 可选：非空时替代库内 {@code visual_config_json} 参与上述重编译（便于抽屉内未保存即试跑 OR/UNION）。
     */
    private String visualConfigJsonOverride;
}
