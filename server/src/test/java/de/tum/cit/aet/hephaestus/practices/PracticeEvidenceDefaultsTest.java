package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class PracticeEvidenceDefaultsTest {

    @ParameterizedTest
    @MethodSource("baselines")
    void shouldCreateTheArtifactBaseline(
        WorkArtifact artifact,
        String profile,
        List<String> required,
        List<String> optional
    ) {
        JsonMapper mapper = JsonMapper.builder().build();
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(mapper, java.time.Clock.systemUTC(), "");

        PracticeEvidenceDeclaration declaration = new PracticeEvidenceDefaults(catalogs).forArtifact(artifact);

        assertThat(declaration.sourceContractVersion()).isEqualTo(new SourceContractVersion("1.0.0"));
        assertThat(declaration.profile()).isEqualTo(new EvidenceProfileId(profile));
        assertThat(declaration.required())
            .extracting(item -> item.sourceKind().value())
            .containsExactlyElementsOf(required);
        assertThat(declaration.optional())
            .extracting(item -> item.sourceKind().value())
            .containsExactlyElementsOf(optional);
        assertThat(declaration.onUnsatisfied()).isEqualTo(PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT);
        assertThat(declaration.blindSpots()).isNotEmpty();
    }

    private static Stream<Arguments> baselines() {
        return Stream.of(
            Arguments.of(
                WorkArtifact.PULL_REQUEST,
                "pull-request-review",
                List.of("scm.pull-request.core", "scm.pull-request.diff"),
                List.of("scm.pull-request.comments")
            ),
            Arguments.of(WorkArtifact.ISSUE, "issue-review", List.of("scm.issue.core"), List.of("scm.issue.comments")),
            Arguments.of(
                WorkArtifact.CONVERSATION_THREAD,
                "conversation-review",
                List.of("slack.conversation.thread"),
                List.of()
            )
        );
    }
}
