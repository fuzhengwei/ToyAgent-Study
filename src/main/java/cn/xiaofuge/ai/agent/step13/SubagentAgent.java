package cn.xiaofuge.ai.agent.step13;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/11 20:58
 */
public interface SubagentAgent extends Agent {

    class Impl implements SubagentAgent {

        private record ChildSpec(String specialty) {
        }

        private static final Map<String, ChildSpec> CHILDREN = new LinkedHashMap<>(Map.of(
                "researcher", new ChildSpec("你是调研子代理。根据任务输出 3 条要点式调研结论，不写代码"),
                "writer", new ChildSpec("你是写作子代理。根据任务输出一段 50 字以内的短文，不解释。")
        ));

        private final ChatModel model;

        public Impl(ChatModel model) {
            this.model = model;
        }

        private final List<Message> parentMessages = new ArrayList<>();

        private static final String SYSTEM_PROMPT = """
                你是主智能体，可以把子任务委托给子代理。需要委托时调用 dispatch_subagent 工具；
                子代理结果已按 tool_call_id 回填后，汇总并用自然语言回答用户，不要重复派遣同一任务。
                注意：task 参数只描述任务本身。选 spawn 时严禁把父会话中的任何信息（偏好、历史、结论）写进 task
                —— spawn 的意义就是全新实例，经 task 泄密会破坏上下文隔离；只有 fork 才能继承父会话上下文。
                """;

        private static Map<String, Object> dispatchTool() {
            Map<String, Object> fn = Json.obj();

            fn.put("name", "dispatch_subagent");
            fn.put("description", "把子任务派遣给一个子代理执行。spawn=全新实例（看不到父对话）；" +
                    "fork=复制父对话消息列表（接着当前话题继续做）。");

            fn.put("parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "name", Map.of("type", "string", "enum", List.copyOf(CHILDREN.keySet()),
                                    "description", "子代理名称"),
                            "mode", Map.of("type", "string", "enum", List.of("spawn", "fork"),
                                    "description", "派遣方式：spawn 全新上下文 / fork 继承父会话"),
                            "task", Map.of("type", "string", "description", "交给子代理的任务描述")),
                    "required", List.of("name", "mode", "task")));

            Map<String, Object> schema = Json.obj();
            schema.put("type", "function");
            schema.put("function", fn);

            return schema;
        }

        private static final List<Map<String, Object>> TOOLS = List.of(dispatchTool());

        @Override
        public String name() {
            return "Step 13 - 子代理";
        }

        @Override
        public String chat(String input) {

            if (parentMessages.isEmpty()) {
                parentMessages.add(Message.system(SYSTEM_PROMPT));
            }

            parentMessages.add(Message.user(input));

            System.out.println(Map.of("type", "tool", "label", "主代理就绪",
                    "detail", "请求携带 tools: dispatch_subagent Schema，可派遣：" + CHILDREN.keySet()
                            + "（父会话消息 " + (parentMessages.size() - 1) + " 条）"));

            for (int round = 1; round <= 4; round++) {
                ChatResponse reply = model.chat(parentMessages, TOOLS);

                if (!reply.hasToolCalls()) {
                    if (reply.content().isBlank()) {
                        continue;
                    }

                    parentMessages.add(Message.assistant(reply.content()));
                    return reply.content();
                }

                parentMessages.add(Message.assistantToolCalls(reply.toolCalls()));

                for (ToolCall call : reply.toolCalls()) {
                    Object args = parseArgs(call.argumentsJson());

                    String childName = Json.str(args, "name");
                    String mode = Json.str(args, "mode");
                    String task = Json.str(args, "task");

                    System.out.println(Map.of("type", "action", "label", "主代理派遣子代理",
                            "detail", "subagent=" + childName + "  mode=" + mode + "  task=" + task));

                    String result = dispatch(childName, mode, task);

                    System.out.println(Map.of("type", "observation", "label", "子代理结果按 tool_call_id 回填",
                            "detail", result));

                    parentMessages.add(Message.tool(call.id(), result));

                }

            }

            return "已达最大派遣轮数!!!";
        }

        private Object parseArgs(String argsJson) {
            try {
                return Json.parse(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            } catch (Exception e) {
                return Json.obj();
            }
        }

        private String dispatch(String childName, String mode, String task) {
            ChildSpec spec = CHILDREN.get(childName);

            if (spec == null) {
                return "没有叫 " + childName + " 的子代理。可用：" + CHILDREN.keySet();
            }

            boolean fork = "fork".equalsIgnoreCase(mode);

            List<Message> childMessages = new ArrayList<>();
            String sys = "【子代理身份】" + spec.specialty()
                    + (fork ? "" : "\n（spawn 模式：全新实例，看不到父会话）");
            childMessages.add(Message.system(sys));

            if (fork) {
                childMessages.addAll(parentMessages.subList(1, parentMessages.size() - 1));
            }

            childMessages.add(Message.user(task));

            String report = "";

            for (int attempt = 1; attempt <= 3 && report.isBlank(); attempt++) {
                report = model.chat(childMessages).trim();
            }

            if (report.isBlank()) {
                report = "（子代理未返回有效内容，网关未命中可用模型，可重试）";
            }

            return report;
        }

    }

    public static void main(String[] args) {
        cn.xiaofuge.ai.llm.ChatModel model = cn.xiaofuge.ai.llm.Models.openAiOrNull();
        System.out.println("[输出] " + new Impl(model).chat("把任务拆给子智能体：调研 Java 21 新特性并总结"));
    }

}
