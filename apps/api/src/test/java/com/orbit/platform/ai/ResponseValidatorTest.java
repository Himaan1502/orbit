package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.service.OscarResponse;
import com.orbit.platform.ai.service.OscarResponse.EvidenceKind;
import com.orbit.platform.ai.service.OscarResponse.Kind;
import com.orbit.platform.ai.service.ResponseValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ResponseValidator} — the {@code oscar-v2} prose-then-sentinel format
 * (ADR-033 §2). Prose that arrived is always real; only no-prose degrades.
 */
class ResponseValidatorTest {

    private final ResponseValidator validator = new ResponseValidator(new ObjectMapper());

    @Test
    void proseOnlyIsAPlainAnswer() {
        OscarResponse r = validator.parse("Start with the reconcile loop.").orElseThrow();
        assertThat(r.kind()).isEqualTo(Kind.answer);
        assertThat(r.message()).isEqualTo("Start with the reconcile loop.");
        assertThat(r.sections()).isNull();
    }

    @Test
    void splitsProseFromTheMetaBlockOnTheSentinel() {
        var s = validator.split("Focus on Kubernetes.\n===OSCAR-META===\n{\"kind\":\"recommendation\"}");
        assertThat(s.prose()).isEqualTo("Focus on Kubernetes.");
        assertThat(s.metaJson()).isEqualTo("{\"kind\":\"recommendation\"}");
    }

    @Test
    void parsesARecommendationWithFactMetricInferenceEvidence() {
        String raw = """
                Assess Kubernetes this week.
                ===OSCAR-META===
                {"kind":"recommendation","sections":{"action":"Self-assess Kubernetes",
                  "why":"largest gap","impact":"readiness +4","evidence":[
                    {"label":"gap","kind":"metric","value":"0.62"},
                    {"label":"target","kind":"fact","value":"Platform Engineer"},
                    {"label":"est","kind":"inference","value":"~2h"}]},
                 "citations":["careerTarget"]}""";
        OscarResponse r = validator.parse(raw).orElseThrow();
        assertThat(r.kind()).isEqualTo(Kind.recommendation);
        assertThat(r.message()).isEqualTo("Assess Kubernetes this week.");
        assertThat(r.sections().evidence()).extracting(OscarResponse.EvidenceRef::kind)
                .containsExactly(EvidenceKind.metric, EvidenceKind.fact, EvidenceKind.inference);
        assertThat(r.citations()).containsExactly("careerTarget");
    }

    @Test
    void unparsableMetaBlockLeavesTheProseStanding() {
        OscarResponse r = validator.parse("Here is the answer.\n===OSCAR-META===\nnot json").orElseThrow();
        assertThat(r.kind()).isEqualTo(Kind.answer);
        assertThat(r.message()).isEqualTo("Here is the answer.");
    }

    @Test
    void aLegacyJsonOnlyReplyIsStillHandled() {
        OscarResponse r = validator.parse(
                "{\"kind\":\"answer\",\"message\":\"Do the migration first.\"}").orElseThrow();
        assertThat(r.message()).isEqualTo("Do the migration first.");
    }

    @Test
    void noProseAtAllIsEmpty() {
        assertThat(validator.parse("")).isEmpty();
        assertThat(validator.parse(null)).isEmpty();
        assertThat(validator.parse("===OSCAR-META===\n{\"kind\":\"answer\"}")).isEmpty();
    }
}
