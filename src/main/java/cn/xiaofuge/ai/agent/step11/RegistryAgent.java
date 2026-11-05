package cn.xiaofuge.ai.agent.step11;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/5 23:15
 */
public interface RegistryAgent extends Agent {

    class Impl implements RegistryAgent {

        private final ChatModel model;

        private final ToolRegistry registry = new ToolRegistry();

        private final Map<String, Runnable> disposers = new HashMap<>();

        public Impl(ChatModel model) {
            this.model = model;
            registerDefaults();
        }

        private void registerDefaults() {
            disposers.put("get_weather", registry.register(new WeatherTool()));
            disposers.put("calculator", registry.register(new CalculatorTool()));
            disposers.put("get_time", registry.register(new TimeTool()));
        }


        @Override
        public String name() {
            return "Step11";
        }

        @Override
        public String chat(String input) {

            if (input.contains("查看工具") || input.contains("工具清单")) {
                return "当前注册表中共有 " + registry.size() + " 个工具：\n" + registry.promptCatalog()
                        + "\n每次对话时，这份清单会经 toolSchema() 组装成 OpenAI tools 字段随请求发给模型"
                        + " —— 注册即生效，卸载即消失。";
            }

            if (input.startsWith("卸载：") || input.startsWith("卸载:")) {
                String name = input.substring(3).trim();
                Runnable d = disposers.remove(name);
                if (d == null) {
                    return "工具 " + name + " 不在注册表中，无法卸载。当前清单：\n" + registry.promptCatalog();
                }
                d.run();

                return "已卸载工具 " + name + "。注销器（disposer）就是注册时返回的 Runnable —— "
                        + "插件停止/卸载时，宿主正是靠它回收工具、系统提示词等注册项。\n当前清单：\n" + registry.promptCatalog()
                        + "\n下一次请求的 tools 字段里将不再出现这个工具，模型自然调不到它。";
            }

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));
            messages.add(Message.user(input));

            for (int round = 1; round <= 5; round++) {
                ChatResponse reply = model.chat(messages, registry.toolSchemas());

                if (!reply.hasToolCalls()) {
                    return reply.content();
                }

                messages.add(Message.assistantToolCalls(reply.toolCalls()));

                for (ToolCall call : reply.toolCalls()) {
                    var tool = registry.lookup(call.name());

                    String result;
                    if (tool.isEmpty()) {
                        result = "未知工具: " + call.name() + "。可用清单：\n" + registry.promptCatalog();
                    } else {
                        try {
                            Object args = Json.parse(call.argumentsJson() == null ? "{}" : call.argumentsJson());
                            result = tool.get().execute(toMap(args));
                        } catch (Exception e) {
                            result = "工具执行失败: " + e.getMessage();
                        }
                    }

                    messages.add(Message.tool(call.id(), result));


                }
            }

            return "已达最大工具轮数（" + 5 + "），为防失控提前终止。";
        }


        /**
         * 天气查询工具：实现协议四件套，即插即用。
         */
        record WeatherTool() implements ToolDefinition {
            private static final Map<String, String> DATA = Map.of(
                    "北京", "晴，26℃", "上海", "多云，28℃", "广州", "雷阵雨，31℃",
                    "深圳", "阵雨，30℃", "杭州", "晴，27℃", "成都", "阴，22℃");

            @Override
            public String name() {
                return "get_weather";
            }

            @Override
            public String description() {
                return "查询指定城市的实时天气";
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("city", Map.of("type", "string", "description", "城市名，如：北京"));
            }

            @Override
            public String execute(Map<String, Object> args) {
                String city = String.valueOf(args.getOrDefault("city", "")).trim();
                return DATA.getOrDefault(city, "未收录城市 " + city + " 的数据");
            }
        }

        private static final String SYSTEM_PROMPT = """
                你是一个可以调用工具的智能体，可用工具以 tools 字段随请求提供；
                需要实时数据或精确计算时调用工具，拿到结果后用自然语言回答用户。
                """;

        @SuppressWarnings("unchecked")
        private static Map<String, Object> toMap(Object o) {
            Map<String, Object> m = new HashMap<>();
            if (o instanceof Map<?, ?> mm) m.putAll((Map<? extends String, ?>) mm);
            return m;
        }


    }

    /**
     * 四则运算工具。
     */
    record CalculatorTool() implements ToolDefinition {
        @Override
        public String name() {
            return "calculator";
        }

        @Override
        public String description() {
            return "计算四则运算，如 12*8";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("expression", Map.of("type", "string", "description", "四则运算表达式，如 12*8"));
        }

        @Override
        public String execute(Map<String, Object> args) {
            Matcher m = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)\\s*")
                    .matcher(String.valueOf(args.getOrDefault("expression", "")));
            if (!m.matches()) return "无法解析表达式";
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(3));
            return switch (m.group(2)) {
                case "+" -> String.valueOf(a + b);
                case "-" -> String.valueOf(a - b);
                case "*" -> String.valueOf(a * b);
                case "/" -> b == 0 ? "除数不能为 0" : String.valueOf(a / b);
                default -> "不支持的运算符";
            };
        }
    }

    /**
     * 时间工具。
     */
    record TimeTool() implements ToolDefinition {
        @Override
        public String name() {
            return "get_time";
        }

        @Override
        public String description() {
            return "获取当前时间";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of();
        }

        @Override
        public String execute(Map<String, Object> args) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    public static void main(String[] args) {
        cn.xiaofuge.ai.llm.ChatModel model = cn.xiaofuge.ai.llm.Models.openAiOrNull();
        boolean realModel = model != null;

        String input = args.length > 0 ? String.join(" ", args) : "帮我查一下北京天气，然后推荐穿搭";

        Impl agent = new Impl(model);
        String answer = agent.chat(input);

        System.out.println("[模型] " + (realModel
                ? "真实 LLM（OpenAI 兼容协议，配置见 config.properties）"
                : "Mock 演示模型（未检测到可用 LLM 配置，回退到离线演示）"));
        System.out.println("[输入] " + input);

        // 注册表里现在有什么，模型在 tools 字段里就能看到什么 —— 这就是本场景的主角
        System.out.println("\n[注册表] 当前注册的工具（即随请求发出的 tools 清单）：");
        System.out.println(agent.registry.promptCatalog().trim()
                .replaceAll("(?m)^- ", "  · "));

        System.out.println("\n[输出] " + answer);
    }

}
