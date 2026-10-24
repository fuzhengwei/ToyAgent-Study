package cn.xiaofuge.ai.llm;

import java.util.List;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/24 20:21
 */
public interface ChatModel {

    String chat(List<Message> messages);

}
