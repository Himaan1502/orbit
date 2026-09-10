package com.orbit.platform.ai.provider;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Shared HTTP plumbing for the providers (ADR-031 §1, ADR-033 §6). One JDK
 * {@link HttpClient} per provider — blocking {@link #post} for the non-streaming
 * call, {@link #postStream} (backed by {@code BodyHandlers.ofLines()}) for SSE.
 * Uniform failure mapping: 429 / 5xx / timeout → retriable; 4xx → permanent.
 */
abstract class HttpAiProviderSupport {

    protected final ObjectMapper json;
    private final HttpClient http;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final long hardStreamCapMs;

    protected HttpAiProviderSupport(ObjectMapper json, String baseUrl,
                                    int connectTimeoutMs, int readTimeoutMs) {
        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = Duration.ofMillis(readTimeoutMs);
        this.hardStreamCapMs = readTimeoutMs * 2L;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    protected abstract String name();

    /** Blocking POST → parsed JSON body. */
    protected JsonNode post(String path, String body, Consumer<HttpRequest.Builder> headers) {
        HttpResponse<byte[]> resp;
        try {
            HttpRequest.Builder b = baseRequest(path, body).header("accept", "application/json");
            headers.accept(b);
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw AiProviderException.transientFailure(name() + " request timed out", 0, e);
        } catch (IOException e) {
            throw AiProviderException.transientFailure(name() + " transport error: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiProviderException.transientFailure(name() + " interrupted", 0, e);
        }
        int sc = resp.statusCode();
        String text = new String(resp.body() == null ? new byte[0] : resp.body(), StandardCharsets.UTF_8);
        if (sc >= 400) {
            throw httpError(sc, text);
        }
        try {
            return json.readTree(text.isBlank() ? "{}" : text);
        } catch (JacksonException e) {
            throw AiProviderException.transientFailure(name() + " unparsable response body", 0, e);
        }
    }

    /**
     * Streaming POST. {@code onDataLine} gets the payload of each SSE
     * {@code data:} frame (never {@code [DONE]}, never a keep-alive comment).
     * The subclass parses each frame, pushes text deltas to its
     * {@link TokenSink}, and assembles the final {@link AiCompletion}.
     */
    protected void postStream(String path, String body, Consumer<HttpRequest.Builder> headers,
                              Consumer<String> onDataLine) {
        HttpResponse<Stream<String>> resp;
        try {
            HttpRequest.Builder b = baseRequest(path, body).header("accept", "text/event-stream");
            headers.accept(b);
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofLines());
        } catch (HttpTimeoutException e) {
            throw AiProviderException.transientFailure(name() + " request timed out", 0, e);
        } catch (IOException e) {
            throw AiProviderException.transientFailure(name() + " transport error: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiProviderException.transientFailure(name() + " interrupted", 0, e);
        }
        int sc = resp.statusCode();
        if (sc >= 400) {
            String errBody;
            try (Stream<String> s = resp.body()) {
                errBody = s.reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append).toString();
            }
            throw httpError(sc, errBody);
        }
        long start = System.currentTimeMillis();
        try (Stream<String> lines = resp.body()) {
            for (Iterator<String> it = lines.iterator(); it.hasNext();) {
                String line = it.next();
                if (System.currentTimeMillis() - start > hardStreamCapMs) {
                    throw AiProviderException.transientFailure(
                            name() + " stream exceeded " + hardStreamCapMs + "ms", 0, null);
                }
                if (line == null || line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("data:")) {
                    String payload = line.substring(5).stripLeading();
                    if (!payload.isEmpty() && !"[DONE]".equals(payload)) {
                        onDataLine.accept(payload);
                    }
                }
                // "event:" / "id:" / "retry:" lines are ignored
            }
        }
    }

    protected JsonNode readTreeSafe(String raw) {
        try {
            return json.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    private HttpRequest.Builder baseRequest(String path, String body) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    }

    private AiProviderException httpError(int sc, String bodyText) {
        boolean retriable = sc == 429 || sc >= 500;
        String detail = truncate(bodyText);
        return retriable
                ? AiProviderException.transientFailure(name() + " HTTP " + sc + ": " + detail, sc, null)
                : AiProviderException.permanent(name() + " HTTP " + sc + ": " + detail, sc);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }
}
