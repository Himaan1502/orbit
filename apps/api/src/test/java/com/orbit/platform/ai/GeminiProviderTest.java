package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.GeminiProvider;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link GeminiProvider} wire-format translation against a stub {@code generateContent} endpoint. */
class GeminiProviderTest {

    private HttpServer server;
    private final AtomicReference<int[]> status = new AtomicReference<>(new int[] {200});
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get()[0], out.length);
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

    private GeminiProvider provider() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties props = new AiProperties("gemini", "groq", 50, 24000, 2000, 3, 2000, 5000,
                new AiProperties.Gemini("test-key", "gemini-3.6-flash", baseUrl),
                new AiProperties.Groq("", "openai/gpt-oss-120b", "http://unused"));
        return new GeminiProvider(new ObjectMapper(), props);
    }

    private AiRequest request() {
        return new AiRequest("system", List.of(new ChatItem.UserText("what now?")),
                List.of(new ToolSpec("get_today_plan", "today", "{\"type\":\"object\"}", ToolSpec.Kind.READ)),
                2000, 0.2);
    }

    @Test
    void parsesTextAndUsage() {
        body.set("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"kind\\":\\"answer\\",\\"message\\":\\"hi\\"}"}]},
                 "finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":8}}""");
        AiCompletion c = provider().complete(request());
        assertThat(c.text()).contains("\"message\":\"hi\"");
        assertThat(c.hasToolCalls()).isFalse();
        assertThat(c.usage().promptTokens()).isEqualTo(120);
        assertThat(c.usage().completionTokens()).isEqualTo(8);
        // the request carried the system instruction + a functionDeclarations tool
        assertThat(lastRequestBody.get()).contains("systemInstruction").contains("functionDeclarations");
    }

    @Test
    void parsesAFunctionCall() {
        body.set("""
                {"candidates":[{"content":{"parts":[
                   {"functionCall":{"name":"get_today_plan","args":{}}}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":50,"candidatesTokenCount":4}}""");
        AiCompletion c = provider().complete(request());
        assertThat(c.hasToolCalls()).isTrue();
        assertThat(c.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.name()).isEqualTo("get_today_plan"));
    }

    @Test
    void rateLimitIsRetriable() {
        status.set(new int[] {429});
        body.set("{\"error\":{\"message\":\"quota\"}}");
        assertThatThrownBy(() -> provider().complete(request()))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.isRetriable()).isTrue();
                    assertThat(e.isRateLimited()).isTrue();
                });
    }

    @Test
    void badRequestIsPermanent() {
        status.set(new int[] {400});
        body.set("{\"error\":{\"message\":\"bad key\"}}");
        assertThatThrownBy(() -> provider().complete(request()))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.isRetriable()).isFalse());
    }
}
