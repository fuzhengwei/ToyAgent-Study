package cn.xiaofuge.ai.agent.step10;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.agent.step03.ReActAgent;
import cn.xiaofuge.ai.agent.step09.McpRealAgent;
import cn.xiaofuge.ai.llm.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/4 20:34
 */
public interface SkillAgent extends Agent {

    record Skill(String name, String level, String description, String instructions, List<Map<String, Object>> tools) {
    }


    class Impl implements SkillAgent {

        private final ChatModel model;

        public Impl(ChatModel model) {
            this.model = model;
        }

        private String execute(String tool, String argsJson) {
            try {
                Object args = Json.parse(argsJson == null ? "{}" : argsJson);
                return switch (tool) {
                    case "get_weather" -> ReActAgent.MockWeather.query(Json.str(args, "city"));
                    case "get_time" -> LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    default -> "未知工具: " + tool;
                };
            } catch (Exception e) {
                return "工具执行失败: " + e.getMessage();
            }
        }

        private static Map<String, Object> tool(String name, String desc, Map<String, Object> params) {
            Map<String, Object> fn = Json.obj();
            fn.put("name", name);
            fn.put("description", desc);
            fn.put("parameters", params);
            Map<String, Object> schema = Json.obj();
            schema.put("type", "function");
            schema.put("function", fn);
            return schema;
        }

        private static Map<String, Object> strParam(String name, String desc) {
            return Map.of("type", "object",
                    "properties", Map.of(name, Map.of("type", "string", "description", desc)),
                    "required", List.of(name));
        }

        private static final List<Skill> SKILLS = List.of(
                new Skill("travel_plan", "L1", "旅游攻略 —— 查天气与时间后生成旅游建议", """
                        你是旅行顾问。请先调用 get_weather 查询用户提到城市的天气，
                        再调用 get_time 获取当前时间，然后基于两项真实数据
                        给出 3 条以内的出行要点。
                        """, List.of(
                        tool("get_weather", "查询指定城市的实时天气", strParam("city", "城市名，如：北京")),
                        tool("get_time", "获取当前日期时间", Map.of("type", "object", "properties", Json.obj())))),
                new Skill("weather_report", "L1", "天气播报 —— 查询并播报某城市天气",
                        """
                                你是天气播报员。请调用 get_weather 查询用户提到城市的天气，
                                用一句话自然地播报结果。""",
                        List.of(tool("get_weather", "查询指定城市的实时天气", strParam("city", "城市名，如：北京")))),
                new Skill("free_chat", "L0", "自由聊天 —— 不用工具的日常对话",
                        "你是友好的中文助手，回答保持简洁。",
                        List.of()));

        private final List<Map<String, Object>> ROUTER_TOOLS = List.of(
                tool("choose_skill", "根据用户输入从已注册技能中选择一项",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "skill", Map.of("type", "string",
                                                "description", "技能名",
                                                "enum", SKILLS.stream().map(Skill::name).toList()),
                                        "reason", Map.of("type", "string", "description", "一句话理由")),
                                "required", List.of("skill"))));

        @Override
        public String name() {
            return "Step10 - 技能";
        }

        @Override
        public String chat(String input) {

            // 1. 技能选择
            StringBuilder catalog = new StringBuilder("你是技能路由器，当前已注册技能：\n");
            for (Skill s : SKILLS) {
                catalog.append("- ").append(s.name()).append("(").append(s.level()).append("):").append(s.description()).append("\n");
            }

            ChatResponse routed = model.chat(List.of(Message.system(catalog.toString()), Message.user(input)), ROUTER_TOOLS);

            Skill chosen = SKILLS.get(SKILLS.size() - 1);

            String reason = "模型未发起路由调用，回退 free_chat";

            if (routed.hasToolCalls()) {

                for (ToolCall call : routed.toolCalls()) {
                    if ("choose_skill".equals(call.name())) {
                        Object args = Json.parse(call.argumentsJson() == null ? "{}" : call.argumentsJson());
                        String want = Json.str(args, "skill");

                        for (Skill s : SKILLS) {
                            if (s.name().equals(want)) {
                                chosen = s;
                                reason = String.valueOf(Json.str(args, "reason"));
                            }
                        }
                    }
                }

            }

            return runSkill(chosen, input);
        }

        private String runSkill(Skill skill, String input) {

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(skill.instructions()));
            messages.add(Message.user(input));

            for (int round = 1; round <= 5; round++) {
                ChatResponse reply = model.chat(messages, skill.tools());

                if (!reply.hasToolCalls()) {
                    return reply.content();
                }

                for (ToolCall call : reply.toolCalls()) {
                    String result = execute(call.name(), call.argumentsJson());
                    messages.add(Message.tool(call.id(), result));
                }
            }

            return "已经达到最大执行轮次，提前终止！死鬼！";
        }

    }

    public static void main(String[] args) {
        cn.xiaofuge.ai.llm.ChatModel model = cn.xiaofuge.ai.llm.Models.openAiOrNull();
        String input = args.length > 0 ? String.join(" ", args) : "帮我做一份去杭州旅行的攻略！";
        System.out.println("[输入] " + input);
        System.out.println("[输出] " + new Impl(model).chat(input));
    }

}
