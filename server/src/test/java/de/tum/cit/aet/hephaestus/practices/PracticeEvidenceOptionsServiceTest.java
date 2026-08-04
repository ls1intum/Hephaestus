package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceSourceOptionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeWorkTypeEvidenceOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Clock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class PracticeEvidenceOptionsServiceTest {

    @Test
    void exposesTheCurrentBaselineAndOnlyProfileCompatibleSources() {
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(
            JsonMapper.builder().build(),
            Clock.systemUTC(),
            "scm.pull-request.core:AUTOMATED_PRACTICE_REVIEW"
        );
        var service = new PracticeEvidenceOptionsService(catalogs, new PracticeEvidenceDefaults(catalogs));

        PracticeEvidenceOptionsDTO result = service.options();

        assertThat(result.workTypes())
            .extracting(PracticeWorkTypeEvidenceOptionsDTO::artifactType)
            .containsExactly(WorkArtifact.PULL_REQUEST, WorkArtifact.ISSUE, WorkArtifact.CONVERSATION_THREAD);
        PracticeWorkTypeEvidenceOptionsDTO pullRequests = result.workTypes().getFirst();
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
            .satisfies(option -> {
                assertThat(option.displayName()).isEqualTo("Pull request details");
                assertThat(option.authorizedForAutomatedReview()).isTrue();
            });
    }
}
