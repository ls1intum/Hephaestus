package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceArtifactOptionsDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceAuthoringDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeEvidenceSourceOptionDTO;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Clock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class PracticeEvidenceAuthoringServiceTest {

    @Test
    void exposesTheCurrentBaselineAndOnlyProfileCompatibleSources() {
        var catalogs = new ClasspathArtifactSourceCatalogRegistry(
            JsonMapper.builder().build(),
            Clock.systemUTC(),
            "scm.pull-request.core:PRACTICE_DETECTION"
        );
        var service = new PracticeEvidenceAuthoringService(catalogs, new PracticeEvidenceDefaults(catalogs));

        PracticeEvidenceAuthoringDTO result = service.options();

        assertThat(result.artifacts())
            .extracting(PracticeEvidenceArtifactOptionsDTO::artifactType)
            .containsExactly(WorkArtifact.PULL_REQUEST, WorkArtifact.ISSUE, WorkArtifact.CONVERSATION_THREAD);
        PracticeEvidenceArtifactOptionsDTO pullRequests = result.artifacts().getFirst();
        assertThat(pullRequests.baseline().required())
            .extracting(requirement -> requirement.sourceKind().value())
            .containsExactly("scm.pull-request.core", "scm.pull-request.diff");
        assertThat(pullRequests.sources())
            .extracting(PracticeEvidenceSourceOptionDTO::sourceKind)
            .contains("scm.pull-request.core", "scm.repository.tree")
            .doesNotContain("scm.issue.core", "slack.conversation.thread");
        assertThat(pullRequests.sources())
            .filteredOn(option -> option.sourceKind().equals("scm.pull-request.core"))
            .singleElement()
            .satisfies(option -> assertThat(option.authorizedForDetection()).isTrue());
    }
}
