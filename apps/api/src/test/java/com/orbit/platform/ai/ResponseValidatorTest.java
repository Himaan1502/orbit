package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.service.OscarResponse;
import com.orbit.platform.ai.service.OscarResponse.EvidenceKind;
import com.orbit.platform.ai.service.OscarResponse.Kind;
import com.orbit.platform.ai.service.ResponseValidator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ResponseValidator} — model output is never trusted (Blueprint §13, ADR-032 §6). */
class ResponseValidatorTest {

    private final ResponseValidator validator = new ResponseValidator(new ObjectMapper());

    @Test
    void parsesACleanAnswerObject() {
        Optional<OscarResponse> r = validator.parse(
                "{\"kind\":\"answer\",\"message\":\"Focus on the reconcile loop.\"}");
        assertThat(r).isPresent();
        assertThat(r.get().kind()).isEqualTo(Kind.answer);
        assertThat(r.get().message()).isEqualTo("Focus on the reconcile loop.");
    }

    @Test
    void toleratesMarkdownFencesAndSurroundingProse() {
        String raw = "Sure! Here's my answer:\n```json\n{\"kind\":\"answer\","
                + "\"message\":\"Do the migration first.\"}\n```\nHope that helps.";
        Optional<OscarResponse> r = validator.parse(raw);
        assertThat(r).isPresent();
        assertThat(r.get().message()).isEqualTo("Do the migration first.");
    }

    @Test
    void parsesARecommendationWithFactMetricInferenceEvidence() {
        String raw = """
                {"kind":"recommendation","message":"Assess Kubernetes.",
                 "sections":{"action":"Self-assess Kubernetes","why":"largest gap",
                   "impact":"readiness +4","evidence":[
                     {"label":"gap","kind":"metric","value":"0.62"},
                     {"label":"target","kind":"fact","value":"Platform Engineer"},
                     {"label":"est","kind":"inference","value":"~2h"}]},
                 "citations":["careerTarget"]}""";
        OscarResponse r = validator.parse(raw).orElseThrow();
        assertThat(r.kind()).isEqualTo(Kind.recommendation);
        assertThat(r.sections().evidence()).extracting(OscarResponse.EvidenceRef::kind)
                .containsExactly(EvidenceKind.metric, EvidenceKind.fact, EvidenceKind.inference);
        assertThat(r.citations()).containsExactly("careerTarget");
    }

    @Test
    void recommendationWithoutSectionsFallsBackToAnswer() {
        OscarResponse r = validator.parse(
                "{\"kind\":\"recommendation\",\"message\":\"hi\"}").orElseThrow();
        assertThat(r.kind()).isEqualTo(Kind.answer);
    }

    @Test
    void emptyOnUnparsableOrMessagelessOutput() {
        assertThat(validator.parse("not json at all")).isEmpty();
        assertThat(validator.parse("{\"kind\":\"answer\"}")).isEmpty();
        assertThat(validator.parse("")).isEmpty();
        assertThat(validator.parse(null)).isEmpty();
    }

    @Test
    void picksUpProseFromAnswerOrTextKeys() {
        assertThat(validator.parse("{\"answer\":\"via the answer key\"}").orElseThrow().message())
                .isEqualTo("via the answer key");
    }
}
