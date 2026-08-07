package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDefinitionOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceSourceOptionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeWorkTypeDefinitionOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.time.Clock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class PracticeDefinitionOptionsServiceTest {

    @Test
    void exposesTheCurrentBaselineAndOnlyProfileCompatibleSources() {
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(JsonMapper.builder().build(), Clock.systemUTC());
        var service = new PracticeDefinitionOptionsService(
            catalogs,
            new PracticeEvidenceDefaults(catalogs),
            PracticeTriggerOptionsFixture.real()
        );

        PracticeDefinitionOptionsDTO result = service.options();

        assertThat(result.workTypes())
            .extracting(PracticeWorkTypeDefinitionOptionsDTO::artifactKind)
            .containsExactly(ArtifactKinds.PULL_REQUEST, ArtifactKinds.ISSUE, ArtifactKinds.CONVERSATION_THREAD);
        PracticeWorkTypeDefinitionOptionsDTO pullRequests = result.workTypes().getFirst();
        assertThat(pullRequests.triggerEvents())
            .filteredOn(option -> option.recommended())
            .extracting(option -> option.event())
            .containsExactly("PullRequestCreated", "PullRequestReady", "PullRequestSynchronized");
        assertThat(pullRequests.triggerEvents())
            .filteredOn(option -> option.event().equals("PullRequestClosed"))
            .singleElement()
            .satisfies(option -> {
                // The label is the domain's own, shown under a "Run mentoring when" legend that already
                // names the work type — so it says what happened, not what it happened to.
                assertThat(option.displayName()).isEqualTo("Closed without merging");
                assertThat(option.recommended()).isFalse();
            });
        assertThat(pullRequests.supportedAutomatedReviewModes()).containsExactly(
            PracticeAutomatedReviewMode.LANGUAGE_MODEL
        );
        assertThat(pullRequests.recommendedRequirements().requiredEvidence())
            .extracting(requirement -> requirement.sourceKind().value())
            .containsExactly("scm.pull-request.core", "scm.pull-request.diff");
        assertThat(pullRequests.allowedSources())
            .extracting(PracticeEvidenceSourceOptionDTO::sourceKind)
            .contains("scm.pull-request.core", "scm.repository.tree")
            .doesNotContain("scm.issue.core", "slack.conversation.thread");
        assertThat(pullRequests.allowedSources())
            .filteredOn(option -> option.sourceKind().equals("scm.pull-request.core"))
            .singleElement()
            .satisfies(option -> assertThat(option.displayName()).isEqualTo("Pull request details"));
    }
}
