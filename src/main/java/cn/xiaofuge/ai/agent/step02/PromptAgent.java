package cn.xiaofuge.ai.agent.step02;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.ChatModel;
import cn.xiaofuge.ai.llm.Message;
import cn.xiaofuge.ai.llm.Models;
import cn.xiaofuge.ai.llm.OpenAiChatModel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/25 08:18
 */
public interface PromptAgent extends Agent {

    class Impl implements PromptAgent {

        private final ChatModel model;
        private final List<Map<String, Object>> lastTrace = new CopyOnWriteArrayList<>();


        private static final String SYSTEM_PROMPT = """
                # 角色
                你是小傅哥的技术助理，专注讲清楚 AI Agent 的原理与工程实践。
                
                # 背景
                读者是正在学习智能体开发的 Java 工程师，熟悉后端技术，但对 AI 领域的新手。
                
                # 任务与约束
                1. 回答使用简体中文，口语化但专业。
                2. 先给结论，再展开解释；涉及概念时给出生活化类比。
                3. 若用户要求“用一句话”，必须严格只回答一句话。
                4. 不确定的内容呀如实说明，禁止编造。
                """;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step02 - 提示词工程";
        }

        @Override
        public String chat(String input) {

            lastTrace.clear();
            lastTrace.add(Map.of("type", "prompt", "label", "注入系统提示词", "detail", "角色/背景/任务 三层结构，长度 " + SYSTEM_PROMPT.length() + " 字符"));

            String answer = model.chat(List.of(
                    Message.system(SYSTEM_PROMPT),
                    Message.user(input)));
            lastTrace.add(Map.of("type", "prompt", "label", "模型按人设作答", "detail", "输出风格由 system 消息约束"));

            return answer;
        }

    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();

        System.out.println(new Impl(model).chat("用一句话解释什么是智能体"));

    }


}
