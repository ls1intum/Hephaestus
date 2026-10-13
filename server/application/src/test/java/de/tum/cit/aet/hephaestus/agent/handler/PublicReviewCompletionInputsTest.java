package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PublicReviewCompletionInputsTest extends BaseUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"summary", "inline"})
    void shouldKeepResultAndProblemReferencesWhenTheNormalizedReviewIsReadAgain(String part) {
        JsonMapper mapper = JsonMapper.builder().build();
        var inputs = new PublicReviewCompletionInputs(
                mock(PracticeResultRepository.class),
                new FeedbackCompositionResultParser(),
                mock(PublicReviewEligibility.class),
                mapper,
                mock(ReviewOutputService.class));
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        JsonNode admitted = mapper.readTree("""
            {"results":[{"practiceResultId":"result-1","practiceSlug":"practice","outcome":"NOT_MET",
              "observations":[{"observationId":"problem-1","anchorable":true,
                "citations":[{"path":"src/A.java","side":"NEW","startLine":4,"endLine":4,"anchorable":true}]}]}]}
            """);
        JsonNode review = mapper.readTree(part.equals("summary") ? """
            {"summary":{"body":"Add a null check.",
              "basedOn":[{"practiceResultId":"result-1","observationId":"problem-1"}]}}
            """ : """
                {"inline":[{"body":"Add a null check.",
                  "basedOn":[{"practiceResultId":"result-1","observationId":"problem-1"}],
                  "anchor":{"observationId":"problem-1","citationIndex":0}}]}
                """);

        var prepared = inputs.prepare(job, review, admitted);
        JsonNode normalized = Objects.requireNonNull(prepared.normalized());
        JsonNode body = part.equals("summary")
                ? normalized.path(part)
                : normalized.path(part).get(0);
        assertThat(body.path("basedOn"))
                .isEqualTo((part.equals("summary")
                                ? review.path(part)
                                : review.path(part).get(0))
                        .path("basedOn"));
        if (part.equals("inline")) {
            assertThat(body.path("anchor"))
                    .isEqualTo(review.path("inline").get(0).path("anchor"));
        }
        var repeated = inputs.prepare(job, normalized, admitted);
        assertThat(repeated.review()).isEqualTo(prepared.review());
        assertThat(repeated.digest()).isEqualTo(prepared.digest());
    }
}
