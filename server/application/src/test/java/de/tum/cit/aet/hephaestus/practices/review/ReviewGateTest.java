package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.ReviewLimit;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.ReviewedBy;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class ReviewGateTest {
    private final PracticeRepository practices = mock(PracticeRepository.class);
    private final ReviewGate gate = new ReviewGate(practices, new JsonMapper());
    private final Workspace workspace = workspace();

    @Test
    void feedbackModeOffStillAdmitsTheReview() {
        var p = practice(1, DefinitionTestFixtures.definition());
        p.setFeedbackMode(FeedbackMode.OFF);
        when(practices.findByWorkspaceIdAndKind(1L, new ItemKind("scm.pull_request")))
                .thenReturn(List.of(p));
        assertThat(gate.evaluate(
                        workspace,
                        snapshot(Map.of()),
                        ReviewOrigin.EVENT,
                        new ItemEventName("scm.pull_request.opened"),
                        List.of()))
                .isInstanceOf(GateDecision.Run.class);
    }

    @Test
    void peopleAndNobodyAreNotExecutedByHephaestus() {
        for (var by : List.of(ReviewedByKind.PEOPLE, ReviewedByKind.NOBODY)) {
            var original = DefinitionTestFixtures.definition();
            var definition = new PracticeDefinition(
                    original.name(),
                    original.reviewOn(),
                    null,
                    original.about(),
                    original.reads(),
                    new ReviewedBy(
                            by,
                            by == ReviewedByKind.PEOPLE
                                    ? new ReviewLimit("MANUAL_REVIEW", "People review this practice.")
                                    : null,
                            List.of()),
                    original.criteria(),
                    null,
                    null,
                    true,
                    original.guidance());
            when(practices.findByWorkspaceIdAndKind(1L, new ItemKind("scm.pull_request")))
                    .thenReturn(List.of(practice(1, definition)));
            assertThat(gate.evaluate(
                            workspace,
                            snapshot(Map.of()),
                            ReviewOrigin.EVENT,
                            new ItemEventName("scm.pull_request.opened"),
                            List.of()))
                    .isInstanceOf(GateDecision.Skip.class);
        }
    }

    @Test
    void automaticStateFiltersDoNotExcludeARequestedReview() {
        var original = DefinitionTestFixtures.definition();
        var d = new PracticeDefinition(
                original.name(),
                new ReviewOn(original.reviewOn().events(), Map.of("state", Set.of("open"))),
                null,
                original.about(),
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                null,
                null,
                true,
                original.guidance());
        when(practices.findByWorkspaceIdAndKind(1L, new ItemKind("scm.pull_request")))
                .thenReturn(List.of(practice(1, d)));
        assertThat(gate.evaluate(
                        workspace,
                        snapshot(Map.of("state", "closed")),
                        ReviewOrigin.EVENT,
                        new ItemEventName("scm.pull_request.opened"),
                        List.of()))
                .isInstanceOf(GateDecision.Skip.class);
        assertThat(gate.evaluate(workspace, snapshot(Map.of("state", "closed")), ReviewOrigin.REQUEST, null, List.of()))
                .isInstanceOf(GateDecision.Run.class);
    }

    @Test
    void feedbackPauseDoesNotStopResultCapture() {
        workspace.getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.PAUSED);
        when(practices.findByWorkspaceIdAndKind(1L, new ItemKind("scm.pull_request")))
                .thenReturn(List.of(practice(1, DefinitionTestFixtures.definition())));
        assertThat(gate.evaluate(workspace, snapshot(Map.of()), ReviewOrigin.REQUEST, null, List.of()))
                .isInstanceOf(GateDecision.Run.class);
    }

    @Test
    void selectsOnlyRequestedPractices() {
        var first = practice(1, DefinitionTestFixtures.definition());
        var second = practice(2, DefinitionTestFixtures.definition());
        when(practices.findByWorkspaceIdAndKind(1L, new ItemKind("scm.pull_request")))
                .thenReturn(List.of(first, second));
        var result = gate.evaluate(workspace, snapshot(Map.of()), ReviewOrigin.REQUEST, null, List.of(2L));
        assertThat(result)
                .isInstanceOfSatisfying(
                        GateDecision.Run.class,
                        r -> assertThat(r.matchedPractices()).containsExactly(second));
    }

    @Test
    void shouldSelectPracticesBeforeRecipientSpecificModelBinding() {
        var practice = practice(1, DefinitionTestFixtures.definition());
        when(practices.findByWorkspaceIdAndKind(1L, new ItemKind("scm.pull_request")))
                .thenReturn(List.of(practice));
        assertThat(gate.evaluate(workspace, snapshot(Map.of()), ReviewOrigin.REQUEST, null, List.of()))
                .isInstanceOf(GateDecision.Run.class);
    }

    @Test
    void rejectsForeignWorkspaceItems() {
        workspace.setId(2L);
        assertThatThrownBy(() -> gate.evaluate(workspace, snapshot(Map.of()), ReviewOrigin.REQUEST, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Practice practice(long id, PracticeDefinition d) {
        return ReviewTestData.practice(1, id, "practice-" + id, d);
    }

    private static Workspace workspace() {
        var ws = new Workspace();
        ws.setId(1L);
        ws.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        ws.getFeatures().setPracticesEnabled(true);
        return ws;
    }

    private static ItemSnapshot snapshot(Map<String, String> state) {
        return new ItemSnapshot(
                new ItemRef(1, 1, new ItemKind("scm.pull_request"), 1L, null, null),
                new ItemVersion("v1", null),
                new IntegrationRef(IntegrationKind.GITHUB, 1, "test", null),
                state,
                List.of(),
                new ItemLabel(1, "Review", "Pull request", 1, "project", null, IntegrationKind.GITHUB));
    }
}
