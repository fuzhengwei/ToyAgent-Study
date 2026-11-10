package cn.xiaofuge.ai.agent.step12;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.ChatModel;
import cn.xiaofuge.ai.llm.Json;
import cn.xiaofuge.ai.llm.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/10 22:25
 */
public interface MultiAgent extends Agent {


    record AgentSpec(String key, String emoji, String system) {
    }

    class Impl implements MultiAgent {

        private static final Map<String, AgentSpec> ROLES = new HashMap<>();

        static {
            ROLES.put("planner", new AgentSpec("planner", "🧭", """
                    你是规划者（Planner）。把用户任务拆解为三个角色的执行计划，只输出 JSON：
                    {"plan": [{"role": "研究员", "task": "..."}, {"role": "写手", "task": "..."}, {"role": "审查员", "task": "..."}]}
                    """));

            ROLES.put("researcher", new AgentSpec("researcher", "🔬",
                    "你是研究员（Researcher）。围绕任务输出 3 条以内研究要点，每条一句话。"));

            ROLES.put("writer", new AgentSpec("writer", "✍️",
                    "你是写手（Writer）。根据任务与素材写一段 100 字以内的短文，直接输出短文。"));

            ROLES.put("reviewer", new AgentSpec("reviewer", "🔍", """
                    你是审查员（Reviewer）。审查短文，只输出 JSON：
                    {"pass": true/false, "comment": "一句话点评"}
                    """));
        }

        private final ChatModel model;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step 12 多智能体协作";
        }

        @Override
        public String chat(String input) {

            Object planObj = tryParse(dispatch("planner", input));

            // 1. 规则者，拆解任务
            List<?> plan = planObj instanceof Map<?, ?> m && m.get("plan") instanceof List<?> l ? l : List.of();
            System.out.println(Map.of(
                    "type", "agent",
                    "label", "🧭 规划者",
                    "detail", "产出 " + plan.size() + " 步计划：" + describe(plan)));

            // 2. 研究员
            String notes = dispatch("researcher", input);
            System.out.println(Map.of("type", "agent", "label", "🔬 研究员（独立上下文）",
                    "detail", clip(notes, 60)));

            // 3. 写手
            String draft = dispatch("writer", input);
            System.out.println(Map.of("type", "agent", "label", "✍️ 写手",
                    "detail", clip(draft, 60)));

            // 4. 审查员
            boolean pass = false;
            for (int round = 1; ; round++) {
                Object review = tryParse(dispatch("reviewer", input));
                pass = Boolean.parseBoolean(String.valueOf(asMap(review).get("pass")));
                String c = Json.str(review, "comment");

                System.out.println(Map.of(
                        "type", pass ? "guard" : "warn",
                        "label", pass ? "✅ 审查员：通过（第 " + round + " 轮）" : "⚠️ 审查员：打回（第 " + round + " 轮）",
                        "detail", c == null ? "" : c));

                if (pass || round == 2) break;

                draft = dispatch("writer", "请按审查意见修改短文。\n原文: " + draft + "\n审查意见: " + Json.str(review, "comment"));

                System.out.println(Map.of("type", "agent", "label", "✍️ 写手修订",
                        "detail", "带着审查意见重写：" + clip(draft, 60)));

            }

            System.out.println(Map.of("type", "final", "label", "🏁 主持者汇总",
                    "detail", (pass ? "一次过审" : "已达护栏上限，带审查意见交付")
                            + "；链路：规划 → 研究 → 写作 ⇄ 审查 → 交付"));

            return draft;
        }

        private String dispatch(String key, String task) {
            AgentSpec spec = ROLES.get(key);
            return model.chat(List.of(Message.system(spec.system), Message.user(task))).trim();
        }

        private Map<?, ?> asMap(Object o) {
            return o instanceof Map<?, ?> m ? m : Map.of();
        }

        private String describe(List<?> plan) {
            StringBuilder sb = new StringBuilder();
            for (Object o : plan) {
                if (o instanceof Map<?, ?> m) {
                    sb.append(String.valueOf(m.get("role"))).append("→");
                }
            }
            if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
            return sb.toString();
        }

        private Object tryParse(String raw) {
            try {
                return Json.parse(raw.replaceFirst("^```\\w*\\s*", "").replaceFirst("```\\s*$", "").trim());
            } catch (Exception e) {
                return Json.obj();
            }
        }

        private String clip(String s, int n) {
            String one = s.replaceAll("\\s+", " ").trim();
            return one.length() > n ? one.substring(0, n) + "…" : one;
        }

    }

    public static void main(String[] args) {
        cn.xiaofuge.ai.llm.ChatModel model = cn.xiaofuge.ai.llm.Models.openAiOrNull();
        System.out.println(new Impl(model).chat("让多个子智能体协作回答：什么是 AI"));
    }

}
