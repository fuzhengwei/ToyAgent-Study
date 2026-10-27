package cn.xiaofuge.ai.agent.step05;

import cn.xiaofuge.ai.agent.Agent;
import cn.xiaofuge.ai.llm.ChatModel;
import cn.xiaofuge.ai.llm.Message;
import cn.xiaofuge.ai.llm.Models;
import cn.xiaofuge.ai.llm.OpenAiChatModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/27 22:19
 */
public interface MemoryAgent extends Agent {

    class Impl implements MemoryAgent {

        private static final int MAX_SHOT_MESSAGES = 4;

        private final ChatModel model;

        private final Path file;

        private final List<Message> shortMemory = new ArrayList<>();

        private final Map<String, String> database = new LinkedHashMap<>();

        private String summary = "";

        public Impl(ChatModel model, Path file) {
            this.model = model;
            this.file = file;
            loadFile();
        }

        public Impl(ChatModel model) {
            this(model, Path.of("runtime", "step05-memory.properties"));
        }

        private void loadFile() {

            Properties properties = new Properties();

            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {

                    properties.load(in);

                    for (String key : properties.stringPropertyNames()) {
                        if (key.startsWith(".db")) database.put(key.substring(3), properties.getProperty(key));
                    }

                } catch (IOException e) {
                    throw new IllegalStateException("读取长期记忆失败", e);
                }
            }

        }

        @Override
        public String name() {
            return "Step05 - 上下文记忆";
        }

        @Override
        public String chat(String input) {

            shortMemory.add(Message.user(input));
            if (shortMemory.size() > MAX_SHOT_MESSAGES) {
                compress();
            }

            String databaseMemory = recallDatabase(input);

            List<Message> context = buildContext(databaseMemory);

            String answer = model.chat(context);

            shortMemory.add(Message.assistant(answer));
            saveFile();

            return answer;
        }

        private void saveFile() {
            try {
                Files.createDirectories(file.getParent());
                Properties properties = new Properties();

                database.forEach((key, value) -> properties.setProperty("db." + key, value));

                try (OutputStream out = Files.newOutputStream(file)) {
                    properties.store(out, "Step05 long-term memory");
                }
            } catch (IOException e) {
                throw new IllegalStateException("写入长期记忆失败", e);
            }
        }

        private List<Message> buildContext(String databaseMemory) {
            List<Message> context = new ArrayList<>();

            context.add(Message.system("""
                    你是一个有记忆的助手。回答时优先使用”记忆区“里的事实。
                    如果记忆里没有，就明确说不知道，不要编造。
                    """));

            context.add(Message.system("【长期记忆/文件】\n" + readMemoryText()));
            context.add(Message.system("【结构记忆/数据库】\n" + databaseMemory));

            if (!summary.isEmpty()) {
                context.add(Message.system("【压缩摘要】\n" + summary));
            }

            context.addAll(shortMemory);

            return context;
        }

        private String readMemoryText() {
            if (database.isEmpty()) return "(空)";
            return String.join("\n", database.keySet().stream().map(key -> key + " = " + database.get("key")).toList());
        }

        private String recallDatabase(String input) {
            String key;
            if (input.contains("咖啡")) key = "coffee";
            else if (input.contains("项目")) key = "project";
            else if (input.contains("名字")) key = "name";
            else key = "";

            if (key.isBlank() || !database.containsKey(key)) {
                return "没有命中";
            }

            return key + " = " + database.get(key);
        }

        private void compress() {
            Message question = shortMemory.remove(0);
            Message answer = shortMemory.remove(0);

            String digest = clip(question.content()) + " -> " + clip(answer.content());

            summary = summary.isEmpty() ? digest : summary + ";" + digest;
        }

        private String clip(String text) {
            String value = text.replaceAll("\\s+", " ").trim();
            return value.length() > 28 ? value.substring(0, 28) + "..." : value;
        }

        public void rememberInDatabase(String key, String value) {
            database.put(key, value);
            saveFile();
        }
    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();

        Impl agent = new Impl(model);

        agent.rememberInDatabase("name", "小傅哥");
        agent.rememberInDatabase("coffee", "冰拿铁");
        agent.rememberInDatabase("project", "ToyAgent");

        String[][] script = {
                {"我叫小傅哥，记住我的名字", "第一轮：名字写入数据库记忆"},
                {"我最近在做 ToyAgent ", "第二轮：项目事实进入短期记忆"},
                {"我想优化上下文压缩", "第三轮：触发滑动窗口压缩"},
                {"新会话测试：我平时喝什么咖啡", "第四轮：短期记忆已重置，仍查询长期记忆"},
                {"那我叫什么名字，在做什么项目？", "第五轮：一次回答引入结构记忆"},
        };

        for (int i = 0; i < script.length; i++) {
            if (i == 3){
                agent.reset();
                System.out.println("\n[操作] 模拟用户重新打开一个会话（reset 短期记忆）");
            }

            String[] step = script[i];
            System.out.println("\n[输入] " + step[0]);
            System.out.println("[输出] " + agent.chat(step[0]));
            System.out.println("[说明] " + step[1]);
        }
    }

}
