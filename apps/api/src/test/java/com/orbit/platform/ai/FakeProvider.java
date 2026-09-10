package com.orbit.platform.ai;

import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiProvider;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.TokenSink;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** A scripted {@link AiProvider} for routing / orchestration tests — no network. */
public final class FakeProvider implements AiProvider {

    private final String name;
    private final Deque<Object> scripted = new ArrayDeque<>();
    public int calls;
    public int streamCalls;
    public boolean configured = true;

    public FakeProvider(String name) {
        this.name = name;
    }

    /** Queue an {@link AiCompletion} or an {@link AiProviderException} for the next call. */
    public FakeProvider script(Object completionOrException) {
        scripted.add(completionOrException);
        return this;
    }

    public static AiCompletion text(String t) {
        return new AiCompletion(t, List.of(), new TokenUsage(10, 5), "STOP");
    }

    public static AiCompletion toolCall(String tool, String argsJson) {
        return new AiCompletion(null,
                List.of(new ChatItem.ToolCall(tool + "#0", tool, argsJson)),
                new TokenUsage(8, 2), "STOP");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String model() {
        return name + "-model";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        calls++;
        return next(TokenSink.DISCARD);
    }

    @Override
    public AiCompletion completeStream(AiRequest request, TokenSink sink) {
        streamCalls++;
        calls++;
        return next(sink);
    }

    private AiCompletion next(TokenSink sink) {
        Object o = scripted.poll();
        if (o instanceof AiProviderException e) {
            throw e;
        }
        if (o instanceof AiCompletion c) {
            if (c.text() != null && !c.text().isEmpty()) {
                sink.onToken(c.text());   // emit as one chunk for streaming tests
            }
            return c;
        }
        throw AiProviderException.transientFailure("no script", 0, null);
    }
}
