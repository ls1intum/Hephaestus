package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
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
        ArtifactKind artifact,
        String profile,
        List<String> required,
        List<String> optional
    ) {
        JsonMapper mapper = JsonMapper.builder().build();
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(mapper, java.time.Clock.systemUTC());

        PracticeAutomatedReviewPolicy requirements = new PracticeEvidenceDefaults(catalogs).forArtifact(artifact);

        assertThat(requirements.sourceContractVersion()).isEqualTo(new SourceContractVersion("1.0.0"));
        assertThat(requirements.evidenceProfile()).isEqualTo(new EvidenceProfileId(profile));
        assertThat(requirements.requiredEvidence())
            .extracting(item -> item.sourceKind().value())
            .containsExactlyElementsOf(required);
        assertThat(requirements.optionalContext())
            .extracting(item -> item.sourceKind().value())
            .containsExactlyElementsOf(optional);
        assertThat(requirements.whenEvidenceIsInsufficient()).isEqualTo(
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW
        );
        assertThat(requirements.knownLimitations()).isNotEmpty();
    }

    private static Stream<Arguments> baselines() {
        return Stream.of(
            Arguments.of(
                ArtifactKinds.PULL_REQUEST,
                "pull-request-review",
                List.of("scm.pull-request.core", "scm.pull-request.diff"),
                List.of("scm.pull-request.comments")
            ),
            Arguments.of(ArtifactKinds.ISSUE, "issue-review", List.of("scm.issue.core"), List.of("scm.issue.comments")),
            Arguments.of(
                ArtifactKinds.CONVERSATION_THREAD,
                "conversation-review",
                List.of("slack.conversation.thread"),
                List.of()
            )
        );
    }
}
