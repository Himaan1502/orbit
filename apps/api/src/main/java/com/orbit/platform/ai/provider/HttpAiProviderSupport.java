package com.orbit.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared plumbing for the HTTP-backed providers (ADR-031 §1): a per-provider
 * {@link RestClient} with explicit timeouts, and uniform mapping of transport
 * failures to {@link AiProviderException} (429 / 5xx / timeout → retriable;
 * 4xx → permanent, fail over with no retry).
 */
abstract class HttpAiProviderSupport {

    protected final ObjectMapper json;
    protected final RestClient http;

    protected HttpAiProviderSupport(ObjectMapper json, String baseUrl,
                                    int connectTimeoutMs, int readTimeoutMs) {
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** POST {@code jsonBody} to {@code path} with {@code headers} applied; return the parsed response. */
    protected JsonNode post(String path, String body, java.util.function.Consumer<org.springframework.http.HttpHeaders> headers) {
        try {
            // read the body as bytes, not String — a provider that answers with
            // an unexpected content-type (Gemini has been seen to send
            // application/octet-stream) would otherwise fail String extraction.
            byte[] raw = http.post()
                    .uri(path)
                    .headers(h -> {
                        h.set("content-type", "application/json");
                        h.set("accept", "application/json");
                        headers.accept(h);
                    })
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            return json.readTree(raw == null || raw.length == 0 ? "{}" : new String(raw, java.nio.charset.StandardCharsets.UTF_8));
        } catch (RestClientResponseException e) {
            int sc = e.getStatusCode().value();
            boolean retriable = sc == 429 || sc >= 500;
            String detail = truncate(e.getResponseBodyAsString());
            if (retriable) {
                throw AiProviderException.transientFailure(
                        name() + " HTTP " + sc + ": " + detail, sc, e);
            }
            throw AiProviderException.permanent(name() + " HTTP " + sc + ": " + detail, sc);
        } catch (RestClientException e) {
            // connection refused, DNS, read timeout — all transient
            throw AiProviderException.transientFailure(name() + " transport error: " + e.getMessage(), 0, e);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw AiProviderException.transientFailure(name() + " unparsable response body", 0, e);
        }
    }

    protected abstract String name();

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }
}
