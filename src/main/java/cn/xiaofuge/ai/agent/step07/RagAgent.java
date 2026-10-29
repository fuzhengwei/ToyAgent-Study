package cn.xiaofuge.ai.agent.step07;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.ChatModel;
import cn.xiaofuge.ai.llm.Message;
import cn.xiaofuge.ai.llm.Models;
import cn.xiaofuge.ai.llm.OpenAiChatModel;

import java.util.*;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/29 23:44
 */
public interface RagAgent extends Agent {

    class Impl implements RagAgent {

        private final ChatModel model;

        private static final List<String> KNOWLEDGE = List.of(
                "ToyAgent 是小傅哥的智能体教学项目，用 12 个渐进式场景讲清楚智能体实现，每个场景只有一个接口类。",
                "智能体 = 大模型（大脑）+ 工具（手脚）+ 记忆（延续）+ 循环（节拍）。ReAct 循环是智能体的经典执行模式。",
                "MCP（Model Context Protocol）是 Anthropic 提出的工具标准化协议，核心方法为 tools/list 与 tools/call，实现工具与智能体解耦。",
                "RAG（检索增强生成）先从知识库检索相关片段，再把片段注入提示词让模型基于事实回答，能缓解幻觉与知识过期问题。",
                "Function Calling 让模型输出结构化工具调用请求（工具名 + 参数），由外部程序执行后将结果回填给模型。",
                "上下文窗口是模型一次能处理的最大 Token 数，超出时需要用滑动窗口、摘要压缩等上下文工程手段保护关键信息。");

        private static final String SYSTEM_PROMPT = """
                   你是知识库问答助手智能体，回答规则；
                   1. 优先依据【参考资料】回答，并标注来源编号，如[1]
                   2. 资料不足以回答时，如实说“知识库中暂无相关内容”，禁止编造
                """;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "";
        }

        @Override
        public String chat(String input) {

            record Hit(String text, double score) {
            }

            List<Hit> ranked = KNOWLEDGE.stream()
                    .map(text -> new Hit(text, score(text, input)))
                    .sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .limit(2)
                    .toList();

            List<Hit> hits = ranked.stream().filter(h -> h.score() > 0).toList();

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));

            if (!hits.isEmpty()) {
                StringBuilder ctx = new StringBuilder("【参考资料】\n");
                for (int i = 0; i < hits.size(); i++) {
                    ctx.append("[").append(i + 1).append("] ").append(hits.get(i).text()).append("\n");
                }
                messages.add(Message.user(ctx.toString()));
            }

            messages.add(Message.user(input));

            return model.chat(messages);
        }

        /**
         * cos（A，B）= Σ（aᵢ·bᵢ）/ (√Σaᵢ² × √Σbᵢ²)
         */
        private double score(String text, String query) {
            if (query.length() < 2) return 0;

            Set<String> grams = new HashSet<>();

            for (int i = 0; i < query.length() - 1; i++) grams.add(query.substring(i, i + 2));

            long hit = 0;

            for (String g : grams) {
                if (text.contains(g)) hit++;
            }

            return grams.isEmpty() ? 0 : (double) hit / grams.size();

        }

        private String clip(String s, int n) {
            return s.length() > n ? s.substring(0, n) + "…" : s;
        }

    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        Impl impl = new Impl(model);
        System.out.println(impl.chat("ToyAgent 是什么？"));
    }

}
