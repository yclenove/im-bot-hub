package com.sov.imhub.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册表：管理所有已注册的插件。
 */
@Slf4j
@Component
public class PluginRegistry {

    private final Map<String, PluginInterface> plugins = new ConcurrentHashMap<>();

    /**
     * 注册插件。
     */
    public void register(PluginInterface plugin) {
        String name = plugin.getName();
        if (plugins.containsKey(name)) {
            log.warn("plugin already registered: {}", name);
            return;
        }
        plugins.put(name, plugin);
        log.info("plugin registered: {} v{}", name, plugin.getVersion());
    }

    /**
     * 获取插件。
     */
    public PluginInterface getPlugin(String name) {
        return plugins.get(name);
    }

    /**
     * 获取所有插件。
     */
    public List<PluginInterface> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }

    /**
     * 按类型获取插件。
     */
    public List<PluginInterface> getPluginsByType(PluginInterface.PluginType type) {
        return plugins.values().stream()
                .filter(p -> p.getType() == type)
                .toList();
    }

    /**
     * 注销插件。
     */
    public void unregister(String name) {
        PluginInterface plugin = plugins.remove(name);
        if (plugin != null) {
            plugin.destroy();
            log.info("plugin unregistered: {}", name);
        }
    }
}
