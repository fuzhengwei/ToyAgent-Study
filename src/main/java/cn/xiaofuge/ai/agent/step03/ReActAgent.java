package cn.xiaofuge.ai.agent.step03;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.agent.step02.PromptAgent;
import cn.xiaofuge.ai.llm.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/26 07:33
 */
public interface ReActAgent extends Agent {

    class Impl implements ReActAgent {

        private final ChatModel model;

        private static final String SYSTEM_PROMPT = """
                你是一个使用 ReAct 模式解决问题的智能体。
                每一轮指输出一个 JSON 对象，格式二选一:
                1. 继续行动: {"thought": "你的思考", "action":"工具名", "action_input": "参数"}
                2. 给出答案: {"thought": "你的思考", "final":"最终答案"}
                
                可用工具:
                - get_weather(city): 查询城市天气
                - calculator(expression): 计算四则运算，如 12*8
                - get_time(): 获取当前时间
                不要输出 JSON 以为的内容。
                """;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step03 - ReAct 智能体（思考-行动-观察循环）";
        }

        @Override
        public String chat(String input) {

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));
            messages.add(Message.user(input));

            for (int step = 1; step <= 5; step++) {
                String raw = model.chat(messages).trim();

                raw = stripCodeFence(raw);

                Object parsed;

                try {
                    parsed = Json.parse(raw);
                } catch (Exception e) {
                    return raw;
                }

                String thought = Json.str(parsed, "thought");
                String fin = Json.str(parsed, "final");

                if (null != fin) {
                    return fin;
                }

                String action = Json.str(parsed, "action");
                String actionInput = Json.str(parsed, "action_input");

                String observation = executeTool(action, actionInput);

                messages.add(Message.assistant(raw));
                messages.add(Message.user("Observation: " + observation));

            }

            return "已达最大步数5，循环终止（生产系统必须有这个保险丝）。";
        }

        private String executeTool(String action, String input) {
            try {
                return switch (action == null ? "" : action) {
                    case "get_weather" -> MockWeather.query(input);
                    case "calculator" -> String.valueOf(calc(input));
                    case "get_time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    default -> "未知工具：" + action;
                };
            } catch (Exception e) {
                return "工具执行失败：" + e.getMessage();
            }
        }

        private double calc(String expr) {
            Matcher m = Pattern.compile("").matcher(expr);

            double a = Double.parseDouble(m.group(1));
            String op = m.group(2);
            double b = Double.parseDouble(m.group(3));

            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                default -> throw new IllegalArgumentException("不支持的运算符" + op);
            };
        }

        private String stripCodeFence(String s) {
            if (s.startsWith("```")) {
                s = s.replaceFirst("^```\\w*\\s*", "").replaceFirst("```\\s*$", "").trim();
            }
            return s;
        }

    }

    final class MockWeather {
        private static final Map<String, String> DATA = Map.of("北京", "晴，26°", "上海", "多云，28°");

        public static String query(String city) {
            String key = (city == null ? "" : city.trim());
            return DATA.getOrDefault(key, "未收录城市" + key + "数据");
        }

    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();

        System.out.println(new ReActAgent.Impl(model).chat("北京今天天气怎么样？"));
    }

}
