package cn.xiaofuge.ai.llm;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/26 22:02
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
