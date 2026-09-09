package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.GroqProvider;
import com.orbit.platform.ai.provider.ToolSpec;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link GroqProvider} OpenAI-compatible wire-format translation against a stub endpoint. */
class GroqProviderTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GroqProvider provider() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties props = new AiProperties("gemini", "groq", 50, 24000, 2000, 3, 2000, 5000,
                new AiProperties.Gemini("", "gemini-3.6-flash", "http://unused"),
                new AiProperties.Groq("groq-key", "openai/gpt-oss-120b", baseUrl));
        return new GroqProvider(new ObjectMapper(), props);
    }

    private AiRequest request() {
        return new AiRequest("system", List.of(new ChatItem.UserText("hi")),
                List.of(new ToolSpec("get_today_plan", "today", "{\"type\":\"object\"}", ToolSpec.Kind.READ)),
                2000, 0.2);
    }

    @Test
    void parsesTextContentAndUsageAndSendsSystemMessage() {
        body.set("""
                {"choices":[{"message":{"content":"{\\"kind\\":\\"answer\\",\\"message\\":\\"ok\\"}"},
                 "finish_reason":"stop"}],"usage":{"prompt_tokens":90,"completion_tokens":6}}""");
        AiCompletion c = provider().complete(request());
        assertThat(c.text()).contains("\"message\":\"ok\"");
        assertThat(c.usage().promptTokens()).isEqualTo(90);
        assertThat(lastRequestBody.get()).contains("\"role\":\"system\"").contains("\"tools\"");
    }

    @Test
    void parsesToolCalls() {
        body.set("""
                {"choices":[{"message":{"content":null,"tool_calls":[
                   {"id":"call_1","type":"function","function":{"name":"get_today_plan","arguments":"{}"}}]},
                 "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":2}}""");
        AiCompletion c = provider().complete(request());
        assertThat(c.hasToolCalls()).isTrue();
        assertThat(c.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(c.toolCalls().get(0).name()).isEqualTo("get_today_plan");
    }
}
