package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldCreateTheArtifactBaseline(ArtifactKind artifact, List<String> required) {
        JsonMapper mapper = JsonMapper.builder().build();
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(mapper, java.time.Clock.systemUTC());

        PracticeAutomatedReviewPolicy requirements = new PracticeEvidenceDefaults(catalogs).forArtifact(artifact);

        assertThat(requirements.sourceContractVersion()).isEqualTo(new SourceContractVersion("1.0.0"));
        assertThat(requirements.requiredNeeds())
            .extracting(item -> item.sourceKind().value())
            .containsExactlyElementsOf(required);
        assertThat(requirements.whenEvidenceIsInsufficient()).isEqualTo(
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW
        );
        assertThat(requirements.knownLimitations()).isNotEmpty();
    }

    private static Stream<Arguments> baselines() {
        return Stream.of(
            // Comments are REQUIRED, not contextual, and that is the deliberate resolution of a
            // disagreement: the defaults offered them as optional context while all 36 shipped practices
            // that read comments required them. Requiring them is what keeps "there were no comments"
            // distinguishable from "we failed to collect the comments".
            Arguments.of(
                ArtifactKinds.PULL_REQUEST,
                List.of("scm.pull-request.comments", "scm.pull-request.core", "scm.pull-request.diff")
            ),
            Arguments.of(ArtifactKinds.ISSUE, List.of("scm.issue.comments", "scm.issue.core")),
            Arguments.of(ArtifactKinds.CONVERSATION_THREAD, List.of("slack.conversation.thread"))
        );
    }
}
