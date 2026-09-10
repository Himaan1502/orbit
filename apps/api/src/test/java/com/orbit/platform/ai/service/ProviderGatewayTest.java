package com.orbit.platform.ai.service;

import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.FakeProvider;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.TokenSink;
import com.orbit.platform.ai.service.ProviderGateway.Attempt;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.orbit.platform.ai.FakeProvider.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link ProviderGateway} routing (ADR-031 §2, ADR-033 §5). */
class ProviderGatewayTest {

    private FakeProvider gemini;
    private FakeProvider groq;

    @BeforeEach
    void setup() {
        gemini = new FakeProvider("gemini");
        groq = new FakeProvider("groq");
    }

    private ProviderGateway gateway(boolean geminiKey, boolean groqKey) {
        AiProperties props = new AiProperties("gemini", "groq", 50, 24000, 2000, 3, 12, 2000, 5000,
                new AiProperties.Gemini(geminiKey ? "k" : "", "gemini-3.6-flash", "http://x"),
                new AiProperties.Groq(groqKey ? "k" : "", "openai/gpt-oss-120b", "http://x"));
        return new ProviderGateway(props, List.of(gemini, groq), new SimpleMeterRegistry());
    }

    private AiRequest req() {
        return new AiRequest("system", List.of(), List.of(), 2000, 0.2);
    }

    @Test
    void primarySucceeds() {
        gemini.script(text("hi"));
        Attempt a = gateway(true, true).callBlocking(req());
        assertThat(a.provider().name()).isEqualTo("gemini");
        assertThat(gemini.calls).isEqualTo(1);
        assertThat(groq.calls).isZero();
    }

    @Test
    void retriesOnceThenFailsOverOnTransient5xx() {
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        groq.script(text("hi"));
        Attempt a = gateway(true, true).callBlocking(req());
        assertThat(a.provider().name()).isEqualTo("groq");
        assertThat(gemini.calls).isEqualTo(2);   // initial + one retry
        assertThat(groq.calls).isEqualTo(1);
    }

    @Test
    void rateLimitFailsOverWithNoRetry() {
        gemini.script(AiProviderException.transientFailure("429", 429, null));
        groq.script(text("hi"));
        Attempt a = gateway(true, true).callBlocking(req());
        assertThat(a.provider().name()).isEqualTo("groq");
        assertThat(gemini.calls).isEqualTo(1);
    }

    @Test
    void badKeyIsPermanentButStillFailsOver() {
        gemini.script(AiProviderException.permanent("401", 401));
        groq.script(text("hi"));
        Attempt a = gateway(true, true).callBlocking(req());
        assertThat(a.provider().name()).isEqualTo("groq");
        assertThat(gemini.calls).isEqualTo(1);   // no retry on a permanent error
    }

    @Test
    void bothDownReturnsNull() {
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        groq.script(AiProviderException.transientFailure("503", 503, null));
        assertThat(gateway(true, true).callBlocking(req())).isNull();
    }

    @Test
    void streamingFailsOverBeforeTheFirstToken() {
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        groq.script(text("streamed answer"));
        StringBuilder got = new StringBuilder();
        Attempt a = gateway(true, true).callStreaming(req(), got::append, new AtomicBoolean(false));
        assertThat(a.provider().name()).isEqualTo("groq");
        assertThat(got.toString()).isEqualTo("streamed answer");
    }

    @Test
    void streamingDoesNotFailOverOnceATokenHasBeenEmitted() {
        AtomicBoolean firstToken = new AtomicBoolean(true);   // pretend a token already streamed
        gemini.script(AiProviderException.transientFailure("mid-stream drop", 0, null));
        assertThatThrownBy(() ->
                gateway(true, true).callStreaming(req(), TokenSink.DISCARD, firstToken))
                .isInstanceOf(AiProviderException.class);
        assertThat(gemini.calls).isEqualTo(1);
        assertThat(groq.calls).isZero();
    }
}
