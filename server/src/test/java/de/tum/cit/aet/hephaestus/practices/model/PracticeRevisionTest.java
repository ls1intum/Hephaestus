package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

class PracticeRevisionTest extends BaseUnitTest {

    @Test
    void shouldSnapshotCompleteDefinitionAndArea() {
        ArrayNode triggers = JsonNodeFactory.instance.arrayNode().add("PullRequestCreated");
        PracticeArea area = new PracticeArea();
        area.setSlug("review-quality");
        area.setName("Review quality");
        area.setDescription("Review work");
        area.setIcon("MessageSquare");
        area.setColor("cyan");
        Practice practice = new Practice();
        practice.setSlug("clear-feedback");
        practice.setName("Clear feedback");
        practice.setArtifactType(WorkArtifact.PULL_REQUEST);
        practice.setTriggerEvents(triggers);
        practice.setCriteria("Detect clear feedback");
        practice.setPrecomputeScript("export default {}");
        practice.setEvidence(PracticeTestEvidence.forArtifact(WorkArtifact.PULL_REQUEST));
        practice.setWhyItMatters("Prevents rework");
        practice.setWhatGoodLooksLike("A concrete suggestion");
        practice.setArea(area);

        PracticeRevision revision = new PracticeRevision(practice, 3);
        triggers.add("ReviewSubmitted");

        assertThat(revision.getRevisionNumber()).isEqualTo(3);
        assertThat(revision.getSlug()).isEqualTo("clear-feedback");
        assertThat(revision.getName()).isEqualTo("Clear feedback");
        assertThat(revision.getArtifactType()).isEqualTo(WorkArtifact.PULL_REQUEST);
        assertThat(TriggerEventsConverter.toList(revision.getTriggerEvents())).containsExactly("PullRequestCreated");
        assertThat(revision.getCriteria()).isEqualTo("Detect clear feedback");
        assertThat(revision.getPrecomputeScript()).isEqualTo("export default {}");
        assertThat(revision.getEvidence()).isEqualTo(practice.getEvidence());
        assertThat(revision.getWhyItMatters()).isEqualTo("Prevents rework");
        assertThat(revision.getWhatGoodLooksLike()).isEqualTo("A concrete suggestion");
        assertThat(revision)
            .extracting(
                PracticeRevision::getAreaSlug,
                PracticeRevision::getAreaName,
                PracticeRevision::getAreaDescription,
                PracticeRevision::getAreaIcon,
                PracticeRevision::getAreaColor
            )
            .containsExactly("review-quality", "Review quality", "Review work", "MessageSquare", "cyan");
        assertThat(revision.getDetectionFingerprint()).hasSize(64);
    }
}
