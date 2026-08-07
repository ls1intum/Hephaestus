package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
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
            new PracticeEvidenceDefaults(catalogs, PracticeSignalOptionsFixture.catalog()),
            PracticeSignalOptionsFixture.real()
        );

        PracticeDefinitionOptionsDTO result = service.options();

        // Ordered by kind rather than by whichever descriptor bean happened to register first: bean
        // order is not stable across builds, and an authoring picker that reshuffles between deploys is
        // worse than one that is merely alphabetical.
        assertThat(result.workTypes())
            .extracting(PracticeWorkTypeDefinitionOptionsDTO::artifactKind)
            .containsExactly(
                ArtifactKinds.CONVERSATION_THREAD,
                ArtifactKind.of("docs.document"),
                ArtifactKinds.ISSUE,
                ArtifactKinds.PULL_REQUEST
            );
        PracticeWorkTypeDefinitionOptionsDTO pullRequests = result
            .workTypes()
            .stream()
            .filter(workType -> workType.artifactKind().equals(ArtifactKinds.PULL_REQUEST))
            .findFirst()
            .orElseThrow();
        assertThat(pullRequests.signals())
            .filteredOn(option -> option.recommended())
            .extracting(option -> option.signal().value())
            .containsExactly("scm.pull_request.opened", "scm.pull_request.ready", "scm.pull_request.synchronized");
        assertThat(pullRequests.signals())
            .filteredOn(option -> option.signal().equals(ScmSignals.PULL_REQUEST_CLOSED))
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
        assertThat(pullRequests.recommendedNeeds())
            .extracting(need -> need.sourceKind().value())
            .containsExactly("scm.pull-request.core", "scm.pull-request.diff", "scm.pull-request.comments");
        assertThat(pullRequests.allowedSources())
            .extracting(PracticeEvidenceSourceOptionDTO::sourceKind)
            .contains("scm.pull-request.core", "scm.repository.tree")
            .doesNotContain("scm.issue.core", "slack.conversation.thread");
        assertThat(pullRequests.allowedSources())
            .filteredOn(option -> option.sourceKind().equals("scm.pull-request.core"))
            .singleElement()
            .satisfies(option -> assertThat(option.displayName()).isEqualTo("Pull request details"));
    }

    @Test
    void saysWhichSourcesCanCarryAClaimAboutWhatIsAbsent() {
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(JsonMapper.builder().build(), Clock.systemUTC());
        var service = new PracticeDefinitionOptionsService(
            catalogs,
            new PracticeEvidenceDefaults(catalogs, PracticeSignalOptionsFixture.catalog()),
            PracticeSignalOptionsFixture.real()
        );

        PracticeDefinitionOptionsDTO result = service.options();

        PracticeWorkTypeDefinitionOptionsDTO pullRequests = result
            .workTypes()
            .stream()
            .filter(workType -> workType.artifactKind().equals(ArtifactKinds.PULL_REQUEST))
            .findFirst()
            .orElseThrow();
        // The two sit at the same required-capture floor, so the flag is the only thing that separates
        // them — an author offered EXHAUSTIVE over linked work items would be sending a request
        // PracticeDefinitionValidator refuses.
        assertThat(pullRequests.allowedSources())
            .filteredOn(option -> option.sourceKind().equals("scm.pull-request.comments"))
            .singleElement()
            .satisfies(option -> assertThat(option.supportsExhaustiveEvidence()).isTrue());
        assertThat(pullRequests.allowedSources())
            .filteredOn(option -> option.sourceKind().equals("scm.linked-work-items"))
            .singleElement()
            .satisfies(option -> assertThat(option.supportsExhaustiveEvidence()).isFalse());
    }
}
