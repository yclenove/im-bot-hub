package com.sov.imhub.plugin;

import java.util.Map;

/**
 * 插件接口：所有插件必须实现此接口。
 */
public interface PluginInterface {

    /**
     * 获取插件名称。
     */
    String getName();

    /**
     * 获取插件版本。
     */
    String getVersion();

    /**
     * 获取插件类型。
     */
    PluginType getType();

    /**
     * 初始化插件。
     */
    void initialize(Map<String, Object> config);

    /**
     * 销毁插件。
     */
    void destroy();

    /**
     * 插件是否已初始化。
     */
    boolean isInitialized();

    /**
     * 插件类型枚举。
     */
    enum PluginType {
        DATASOURCE,  // 数据源插件
        PLATFORM,    // 平台插件
        RENDERER,    // 渲染插件
        AUTH         // 认证插件
    }
}
