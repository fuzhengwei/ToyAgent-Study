package cn.xiaofuge.ai.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/10/24 20:54
 */
public final class Models {

    private static String baseUrl;
    private static String apiKey;
    private static String modelName;

    static {
        load();
    }

    private static void load() {
        Properties properties = new Properties();
        Path conf = Path.of("config.properties");

        try (InputStream in = Files.newInputStream(conf)) {
            properties.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        baseUrl = properties.getProperty("base-url");
        apiKey = properties.getProperty("api-key");
        modelName = properties.getProperty("model");
    }

    public static OpenAiChatModel openAiOrNull() {
        return new OpenAiChatModel(baseUrl, apiKey, modelName);
    }

}
