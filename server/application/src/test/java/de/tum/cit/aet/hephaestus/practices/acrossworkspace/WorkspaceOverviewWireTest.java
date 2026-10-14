package de.tum.cit.aet.hephaestus.practices.acrossworkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.PracticeWorkspaceOverviewDTO;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.PracticeWorkspaceTileDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WorkspaceOverviewWireTest extends BaseUnitTest {
    @Test
    void overviewContainsCountsWithoutDeveloperIdentityOrPrivateGuidance() {
        var tile = new PracticeWorkspaceTileDTO("practice", "Practice", null, 2, new OutcomeVectorDTO(1, 1, 0, 0));
        var dto = new PracticeWorkspaceOverviewDTO(2, List.of(tile));
        var tree = new JsonMapper().valueToTree(dto);
        assertThat(tree.path("developers").asInt()).isEqualTo(2);
        assertThat(tree.toString()).doesNotContain("developerId", "feedbackBody", "toWorkOn");
    }

    @Test
    void overviewCopiesItsLists() {
        var input = new ArrayList<PracticeWorkspaceTileDTO>();
        var dto = new PracticeWorkspaceOverviewDTO(0, input);
        input.clear();
        assertThatThrownBy(() -> dto.practices().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
