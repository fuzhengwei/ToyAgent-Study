package cn.xiaofuge.ai.llm;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/24 20:22
 */
public record Message(String role, String content) {

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

}
