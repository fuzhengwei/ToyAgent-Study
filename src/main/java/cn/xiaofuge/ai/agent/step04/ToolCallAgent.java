package cn.xiaofuge.ai.agent.step04;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.agent.step03.ReActAgent;
import cn.xiaofuge.ai.llm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/26 22:16
 */
public interface ToolCallAgent extends Agent {

    class Impl implements ToolCallAgent {

        private final ChatModel model;

        private static final String SYSTEM_PROMPT = """
                你是一个可以调用工具的智能体。需要实时数据或精确计算时调用工具，
                拿到工具结果后再用自然语言回答用户。
                """;

        private static Map<String, Object> tool(String name, String desc, Map<String, Object> params) {
            Map<String, Object> fn = Json.obj();
            fn.put("name", name);
            fn.put("description", desc);
            fn.put("parameters", params);

            Map<String, Object> schema = Json.obj();
            schema.put("type", "function");
            schema.put("function", "fn");

            return schema;
        }

        private static final List<Map<String, Object>> TOOLS = List.of(
                tool("get_weather", "查询指定城市的实时天气",
                        Map.of("type", "object", "properties",
                                Map.of("city",
                                        Map.of("type", "string",
                                                "description", "城市名，如：北京")))),
                tool("calculator", "计算四则运算表达式",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "expression", Map.of("type", "string", "description", "四则运算表达式，如 12*8")),
                                "required", List.of("expression"))));

        private static final int MAX_ROUND = 5;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step04 - 工具调用（function calling）";
        }

        @Override
        public String chat(String input) {

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));
            messages.add(Message.user(input));

            for (int step = 1; step <= MAX_ROUND; step++) {
                ChatResponse reply = model.chat(messages, TOOLS);

                if (!reply.hasToolCalls()) {
                    return reply.content();
                }

                messages.add(Message.assistantToolCalls(reply.toolCalls()));

                for (ToolCall call : reply.toolCalls()) {
                    String result = execute(call.name(), call.argumentsJson());
                    messages.add(Message.tool(call.id(),result));
                }

            }

            return "已达最大工具轮数（" + MAX_ROUND + "），为防失控提前终止。";
        }

        private String execute(String tool, String argsJson) {
            try {
                Object args = Json.parse(argsJson == null ? "{}" : argsJson);
                return switch (tool) {
                    case "get_weather" -> ReActAgent.MockWeather.query(Json.str(args, "city"));
                    case "calculator" -> {
                        Matcher m = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)\\s*")
                                .matcher(String.valueOf(Json.str(args, "expression")));
                        if (!m.matches()) yield "无法解析表达式";
                        double a = Double.parseDouble(m.group(1));
                        double b = Double.parseDouble(m.group(3));
                        yield switch (m.group(2)) {
                            case "+" -> String.valueOf(a + b);
                            case "-" -> String.valueOf(a - b);
                            case "*" -> String.valueOf(a * b);
                            case "/" -> b == 0 ? "除数不能为 0" : String.valueOf(a / b);
                            default -> "不支持的运算符";
                        };
                    }
                    default -> "未知工具：" + tool;
                };
            } catch (Exception e) {
                return "工具执行失败：" + e.getMessage();
            }
        }

    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();

        System.out.println(new Impl(model).chat("帮我查一下北京天气，然后推荐穿搭"));

    }

}
