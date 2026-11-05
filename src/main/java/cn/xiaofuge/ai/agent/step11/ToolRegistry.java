package cn.xiaofuge.ai.agent.step11;

import java.util.*;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/5 23:10
 */
public final class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new HashMap<>();

    public synchronized Runnable register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
        return () -> {
            synchronized (ToolRegistry.this) {
                tools.remove(tool.name());
            }
        };
    }

    public synchronized Optional<ToolDefinition> lookup(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name));
    }

    public synchronized int size() {
        return tools.size();
    }

    /** 是否为空。 */
    public synchronized boolean isEmpty() {
        return tools.isEmpty();
    }

    /** 清空注册表（重置场景时使用）。 */
    public synchronized void clear() {
        tools.clear();
    }

    /**
     * 把当前目录收集成 OpenAI tools 协议的完整清单（每个工具调 {@link ToolDefinition#toolSchema()}）——
     * <b>这个列表就是请求体里的 tools 字段</b>：模型能调用什么，完全由注册表当前内容决定。
     * 注册即生效，注销即消失，无需改任何代码或提示词。
     */
    public synchronized List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDefinition t : tools.values()) {
            schemas.add(t.toolSchema());
        }
        return schemas;
    }

    /**
     * 人读版工具清单（本地「查看工具清单」指令、轨迹展示用）——
     * 协议层的清单见 {@link #toolSchemas()}，这份只是同一目录的文本投影。
     */
    public synchronized String promptCatalog() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition t : tools.values()) {
            sb.append("- ").append(t.name()).append("(");
            int i = 0;
            for (Map.Entry<String, Object> p : t.parameters().entrySet()) {
                if (i++ > 0) sb.append(", ");
                sb.append(p.getKey());
                Object type = p.getValue() instanceof Map<?, ?> m ? m.get("type") : null;
                if (type != null) sb.append(": ").append(type);
            }
            sb.append(") — ").append(t.description()).append("\n");
        }
        return sb.toString();
    }

}
