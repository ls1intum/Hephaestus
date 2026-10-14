package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeTest extends BaseUnitTest {

    @Test
    void shouldKeepTheAuthoredItemKindWhenReviewBindingsChange() {
        var mapper = JsonMapper.builder().build();
        var practice = practice();
        var original = DefinitionTestFixtures.definition();
        practice.setCurrentVersion(new PracticeVersion(practice, 1, mapper.valueToTree(original), null));
        var eventFree = new PracticeDefinition(
                original.name(),
                new ReviewOn(List.of(), Map.of()),
                original.appliesWhen(),
                original.about(),
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                original.script(),
                original.scan(),
                original.lineNotes(),
                original.guidance());
        practice.setCurrentVersion(new PracticeVersion(practice, 2, mapper.valueToTree(eventFree), null));
        assertThat(practice.getKind()).isEqualTo(new ItemKind("scm.pull_request"));
        assertThat(practice.getCurrentVersion()
                        .getDefinition()
                        .path("reviewOn")
                        .path("events")
                        .isEmpty())
                .isTrue();
    }

    @Test
    void shouldKeepThePinnedDefinitionWhenWorkspaceControlsChange() {
        var mapper = JsonMapper.builder().build();
        var practice = practice();
        var definition = mapper.valueToTree(DefinitionTestFixtures.definition());
        var pinned = new PracticeVersion(practice, 1, definition, null);
        practice.setCurrentVersion(pinned);
        var group = new PracticeGroup();
        group.setSlug("review-quality");
        group.setName("Review quality");
        practice.setGroup(group);
        practice.setPosition(3);
        practice.setFeedbackMode(FeedbackMode.OFF);
        assertThat(practice.getCurrentVersion()).isSameAs(pinned);
        assertThat(pinned.getDefinition()).isEqualTo(definition);
        assertThat(practice.getKind()).isEqualTo(new ItemKind("scm.pull_request"));
    }

    private static Practice practice() {
        var workspace = new Workspace();
        workspace.setId(41L);
        var practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setKind(new ItemKind("scm.pull_request"));
        practice.setSlug("clear-feedback");
        return practice;
    }
}
