package cn.xiaofuge.ai.agent;

import java.util.List;
import java.util.Map;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/24 20:48
 */
public interface Agent {

    String name();

    String chat(String input);

    default List<Map<String, Object>> trace() {
        return List.of();
    }

    default void reset() {
    }

}
