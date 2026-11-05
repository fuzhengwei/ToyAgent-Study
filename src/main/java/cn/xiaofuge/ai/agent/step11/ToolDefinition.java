package cn.xiaofuge.ai.agent.step11;

import java.util.List;
import java.util.Map;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/5 23:11
 */
public interface ToolDefinition {

    /** 工具名：模型 tool_calls 中 function.name 的取值。 */
    String name();

    /** 一句话用途：进入 tools 协议的 function.description，是模型选择工具的主要依据。 */
    String description();

    /**
     * 参数声明：标准 JSON Schema 的 properties 片段 ——
     * 参数名 → {"type": "...", "description": "..."}。
     * 注意这是真实协议形态，不再用"参数名 → 文字说明"的教学化简版。
     */
    Map<String, Object> parameters();

    /** 必填参数列表，进入 Schema 的 required 字段；默认全部必填。 */
    default List<String> required() {
        return List.copyOf(parameters().keySet());
    }

    /**
     * 组装 OpenAI tools 协议条目（即请求体 tools 数组的一个元素）：
     * <pre>
     * {"type": "function", "function": {"name": ..., "description": ..., "parameters": {...}}}
     * </pre>
     * 工具的自描述能力就在这里 —— 注册表把这些条目收集起来随每次请求发给模型。
     */
    default Map<String, Object> toolSchema() {
        Map<String, Object> fn = cn.xiaofuge.ai.llm.Json.obj();
        fn.put("name", name());
        fn.put("description", description());
        Map<String, Object> schema = cn.xiaofuge.ai.llm.Json.obj();
        schema.put("type", "object");
        schema.put("properties", parameters());
        schema.put("required", required());
        fn.put("parameters", schema);

        Map<String, Object> wire = cn.xiaofuge.ai.llm.Json.obj();
        wire.put("type", "function");
        wire.put("function", fn);
        return wire;
    }

    /** 执行工具：输入参数（来自 tool_calls 的 arguments JSON），输出给模型看的观察结果。 */
    String execute(Map<String, Object> args) throws Exception;

    /** 是否可并行执行（fs 读类 true，shell 写类 false）—— 预留并行调度语义。 */
    default boolean concurrencySafe() {
        return false;
    }

}
