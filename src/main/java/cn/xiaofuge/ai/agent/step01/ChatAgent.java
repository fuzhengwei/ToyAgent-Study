package cn.xiaofuge.ai.agent.step01;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.ChatModel;
import cn.xiaofuge.ai.llm.Message;
import cn.xiaofuge.ai.llm.Models;
import cn.xiaofuge.ai.llm.OpenAiChatModel;

import java.util.List;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/24 20:50
 */
public interface ChatAgent extends Agent {

    class Impl implements ChatAgent {

        private final ChatModel model;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step01 - 对话智能体（最小 MVP）";
        }

        @Override
        public String chat(String input) {
            return model.chat(List.of(Message.user(input)));
        }
    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();

        System.out.println(new Impl(model).chat("用一句话介绍你自己"));
    }

}
