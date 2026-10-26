package cn.xiaofuge.ai.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiChatModel implements ChatModel {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // http://127.0.0.1:8777/ http://127.0.0.1:8777
    public OpenAiChatModel(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String chat(List<Message> messages) {

        LinkedHashMap<Object, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        body.put("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("模型调用失败 HTTP " + response.statusCode() + ": " + response.body());
            }

            // 解析响应体
            Object root = Json.parse(response.body());

            Object choices = ((Map<?, ?>) root).get("choices");
            Object first = ((List<?>) choices).get(0);
            Object message = ((Map<?, ?>) first).get("message");

            return String.valueOf(((Map<?, ?>) message).get("content"));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ChatResponse chat(List<Message> messages, List<Map<String, Object>> tools) {
        // 1. 组装请求体，model + messages + tools
        LinkedHashMap<Object, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(OpenAiChatModel::toWire));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        body.put("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("模型调用失败 HTTP " + response.statusCode() + ": " + response.body());
            }

            // 解析响应体
            Object root = Json.parse(response.body());
            Object message = ((Map<?, ?>) ((List<?>) ((Map<?, ?>) root).get("choices")).get(0)).get("messages");

            Object contentObj = ((Map<?, ?>) message).get("content");
            String content = contentObj == null ? "" : String.valueOf(contentObj);

            List<ToolCall> calls = new ArrayList<>();

            Object rawCalls = ((Map<?, ?>) message).get("tool_calls");
            if (rawCalls instanceof List<?> list) {
                for (Object item : list) {
                    Map<?, ?> call = (Map<?, ?>) item;
                    Map<?, ?> fn = (Map<?, ?>) call.get("function");

                    calls.add(new ToolCall(
                            String.valueOf(call.get("id")), Json.str(fn, "name"), Json.str(fn, "arguments")));
                }
            }

            return new ChatResponse(content, List.copyOf(calls));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private static Map<String, Object> toWire(Message m) {

        Map<String, Object> wire = Json.obj();
        wire.put("role", m.role());

        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            wire.put("tool_calls", m.toolCalls().stream().map(tc -> {
                Map<String, Object> fn = Json.obj();
                fn.put("name", tc.name());
                fn.put("arguments", tc.argumentsJson());

                Map<String, Object> call = Json.obj();
                call.put("id", tc.id());
                call.put("type", "function");
                call.put("function", fn);

                return call;
            }));
        } else {
            wire.put("content", m.content() == null ? "" : m.content());
            if ("tool".equals(m.role())) {
                wire.put("tool_call_id", m.toolCallId());
            }
        }

        return wire;
    }

}
