package cn.xiaofuge.ai.llm;

import java.util.List;
import java.util.Map;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/24 20:21
 */
public interface ChatModel {

    String chat(List<Message> messages);

    /**
     * fuzhengwei@anonymous ~ % curl http://127.0.0.1:8777/v1/chat/completions \
     * -H "Content-Type: application/json" \
     * -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
     * -d '{
     * "model": "deepseek-v4-flash",
     * "messages": [{"role": "user", "content": "北京天气怎么样？"}],
     * "tools": [{
     * "type": "function",
     * "function": {
     * "name": "get_weather",
     * "description": "查询指定城市的实时天气",
     * "parameters": {
     * "type": "object",
     * "properties": {"city": {"type": "string", "description": "城市名，如：北京"}},
     * "required": ["city"]
     * }
     * }
     * }]
     * }'
     * {"choices":[{"finish_reason":"tool_calls","index":0,"logprobs":null,"matched_stop":null,"message":{"content":"","reasoning_content":"The user wants to know the weather in Beijing. I should call the get_weather tool for Beijing.","role":"assistant","tool_calls":[{"function":{"arguments":"{\"city\": \"北京\"}","name":"get_weather"},"id":"call_eafb5d4bc3214f7c8e9402f5","index":0,"type":"function"}]}}],"created":1793023200,"id":"8da4566827c04b719757fa4bf77dc790","metadata":{"weight_version":"default","weight_versions":[{"end":67,"start":0,"version":"default"}]},"model":"deepseek_for_coding","object":"chat.completion","usage":{"completion_tokens":67,"prompt_tokens":292,"prompt_tokens_details":{"cached_tokens":256},"reasoning_tokens":22,"total_tokens":359}}%
     */
    default ChatResponse chat(List<Message> messages, List<Map<String, Object>> tools) {
        return new ChatResponse(chat(messages), List.of());
    }

}
