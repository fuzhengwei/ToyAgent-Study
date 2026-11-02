package cn.xiaofuge.ai.agent.step08;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.agent.step07.RagAgent;
import cn.xiaofuge.ai.llm.ChatModel;
import cn.xiaofuge.ai.llm.Message;
import cn.xiaofuge.ai.llm.Models;
import cn.xiaofuge.ai.llm.OpenAiChatModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/11/2 22:12
 */
public interface WikiAgent extends Agent {

    class Impl implements WikiAgent {

        private final ChatModel model;

        private static final Map<String, String> BASE_WIKI = Map.of(
                "架构约定", "ToyAgent 是零依赖教学项目；一个接口类就是一个智能体场景（step01~26），全部实现放在 Impl 内部类里，禁止引入第三方依赖；模型层统一走 ChatModel 模型",
                "代码规范", "Java 17 语法；类与核心方法必须有中文 Javadoc 说明教学意图；trace 事件用 type/label/detail 三段式；JSON 一律走自研 Json 工具类。",
                "部署流程", "javac -d target/classes 编译后 java -cp target/classes cn.xiaofuge.ai.Application 启动，默认端口 8099，可用环境变量 TOY_AGENT_PORT 覆盖；前端静态资源在 web/ 目录由服务直接托管。",
                "业务规则", "配置只保存在各人浏览器 localStorage 随请求生效，服务端 config.properties 仅作默认兜底；未配置 API Key 时所有场景回落 Mock 演示模型。"
        );

        private final Map<String, String> userWiki = new LinkedHashMap<>();

        private static final Path USER_WIKI_FILE = Path.of("wiki", "user-wiki.md");

        private static final String SYSTEM_PROMPT = """
                你是项目 Wiki 问答智能体。回答规则：
                1. 优先依据【项目 Wiki】的条目回答，并标注来源条目名，如（来源：架构约定）。
                2. Wiki 条目不足以回答时，如实说"项目 Wiki 中暂无相关条目"，并提示可以用「编译：主题：内容」沉淀该知识。禁止编造。
                """;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step08 - LLM-WIKI 知识编译";
        }

        @Override
        public String chat(String input) {

            if (input.startsWith("编译:") || input.startsWith("编译：") || input.startsWith("记住:") || input.startsWith("记住：")) {
                return compile(input);
            }


            Map<String, String> wiki = currentWiki();
            System.out.println("[ToyAgent][Wiki] 加载 Wiki：内置 " + BASE_WIKI.size() + " + 已编译 " + userWiki.size() + " = " + wiki.size() + " 条");

            record Hit(String topic, String content, double score) {
            }

            List<Hit> ranked = wiki.entrySet().stream()
                    .map(e -> new Hit(e.getKey(), e.getValue(), score(e.getKey() + e.getValue(), input)))
                    .sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .limit(2)
                    .toList();

            List<Hit> hits = ranked.stream().filter(h -> h.score() > 0).toList();

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));

            if (!hits.isEmpty()) {
                StringBuilder ctx = new StringBuilder("【项目 Wiki】\n");

                for (Hit h : hits) {
                    ctx.append("◆ ").append(h.topic()).append(": ").append(h.content()).append("\n");
                }

                messages.add(Message.user(ctx.toString()));
            }

            messages.add(Message.user(input));

            String answer = model.chat(messages);

            System.out.println("[ToyAgent][Wiki] 作答完成：" + (hits.isEmpty() ? "无条目命中，提示可编译" : "基于 " + hits.size() + " 条"));
            System.out.println("[ToyAgent][Wiki] 回答：" + answer.replaceAll("\\s+", " "));

            return answer;
        }

        private Map<String, String> currentWiki() {
            Map<String, String> all = new LinkedHashMap<>();

            all.putAll(BASE_WIKI);
            all.putAll(userWiki);

            return all;
        }


        private String compile(String input) {
            String body = input.substring(3).trim();

            int sep = body.indexOf('：');
            if (sep < 0) sep = body.indexOf(':');

            if (body.isBlank() || sep <= 0 || sep == body.length() - 1) {
                return "编译格式不对哦。请用：「编译：主题：内容」，例如「编译：发布流程：每周三灰度，周五全量上线」。";
            }

            String topic = body.substring(0, sep).trim();
            String content = body.substring(sep + 1).trim();

            boolean update = userWiki.containsKey(topic);

            userWiki.put(topic, content);

            persistUserWiki();

            System.out.println("[ToyAgent][Wiki] " + (update ? "更新" : "新增") + "条目：◆ " + topic + "（已持久化到 " + USER_WIKI_FILE + "）");

            return "已编译进项目 Wiki：◆ " + topic + "\n\n" + content + "\n\n之后所有提问都会带上这条知识（已持久化，重启不丢）。试试问我相关内容。";

        }

        private void persistUserWiki() {
            try {
                Files.createDirectories(USER_WIKI_FILE.getParent());
                StringBuilder sb = new StringBuilder("# ToyAgent 用户 Wiki（由页面编译生成）\n\n");
                userWiki.forEach((k, v) -> sb.append("## ").append(k).append("\n").append(v).append("\n\n"));
                Files.writeString(USER_WIKI_FILE, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("[ToyAgent] 写入 user-wiki 失败: " + e.getMessage());
                System.out.println("[ToyAgent][Wiki] 持久化失败：" + e.getMessage() + "（条目仅保存在内存，重启会丢）");
            }
        }

        private double score(String text, String query) {
            if (query.length() < 2) return 0;
            Set<String> grams = new java.util.HashSet<>();
            for (int i = 0; i < query.length() - 1; i++) grams.add(query.substring(i, i + 2));
            long hit = 0;
            for (String g : grams) {
                if (text.contains(g)) hit++;
            }
            return grams.isEmpty() ? 0 : (double) hit / grams.size();
        }

    }


    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        Impl impl = new Impl(model);
        System.out.println(impl.chat("怎么配置 API Key？"));
//        System.out.println(impl.chat("ToyAgent 是零依赖教学项目学习经验"));
    }

}
