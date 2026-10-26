package cn.xiaofuge.ai.llm;

import java.util.List;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/26 22:01
 */
public record ChatResponse(String content, List<ToolCall> toolCalls) {

    public boolean hasToolCalls(){
        return toolCalls != null && !toolCalls.isEmpty();
    }

}
