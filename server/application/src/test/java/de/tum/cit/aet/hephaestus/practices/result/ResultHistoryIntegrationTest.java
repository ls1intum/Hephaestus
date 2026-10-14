package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.review.ReviewAdmissionData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAttemptGuard;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBudget;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewModelBindings;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.node.ObjectNode;

@Import({ReviewIntegrationData.class, ReviewAdmissionData.class})
class ResultHistoryIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ResultHistoryQuery history;

    @Autowired
    ReviewAdmissionData admissions;

    @Autowired
    ResultRepository read;

    @Autowired
    ResultMarkService marks;

    @Autowired
    PracticeVersionService versions;

    @Autowired
    PracticeVersionRepository historicalVersions;

    @MockitoBean
    ResultSourceAuthorization authorization;

    @MockitoBean
    HistoryCustodyQuery custody;

    @MockitoBean
    ReviewRuntimeQuery runtime;

    @MockitoBean
    ReviewModelBindings models;

    @MockitoBean
    ReviewBudget budget;

    @MockitoBean
    ReviewAttemptGuard ownership;

    ReviewIntegrationData.Scenario scenario;

    long scenarioAccountId;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
        scenarioAccountId = Objects.requireNonNull(scenario.account().getId());
        when(authorization.custody(any()))
                .thenReturn(new HistoryCustody(true, List.of(), List.of(), List.of(UUID.randomUUID())));
        when(authorization.authorized(any(), any(), any())).thenReturn(true);
    }

    @Test
    void historyRetainsMarkedAndEndedFactsWithoutResurrectingTheirClaims() {
        var old = data.result(scenario, ResultOutcome.MET, Instant.now().minusSeconds(120));
        data.end(old, Instant.now().minusSeconds(60));
        var current = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        marks.create(
                current.getWorkspaceId(),
                current.getId(),
                ResultMarkKind.INCORRECT,
                scenarioAccountId,
                "The claim has the wrong basis");
        var rows = history.forDeveloper(scenario.workspace().getId(), scenarioAccountId, Instant.EPOCH, 20);
        assertThat(rows).extracting(ResultHistoryEntry::resultId).containsExactly(current.getId(), old.getId());
        assertThat(rows.getFirst().activeMarks()).containsExactly(ResultMarkKind.INCORRECT);
        assertThat(rows.getLast().endedAt()).isNotNull();
    }

    @Test
    void pinnedCriteriaAndVersionDoNotChangeWhenCurrentDefinitionChanges() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var original = DefinitionTestFixtures.definition();
        var changed = new PracticeDefinition(
                original.name(),
                original.reviewOn(),
                original.appliesWhen(),
                original.about(),
                original.reads(),
                original.reviewedBy(),
                "Use the newly revised standard.",
                original.script(),
                original.scan(),
                original.lineNotes(),
                original.guidance());
        versions.append(scenario.practice(), changed, scenarioAccountId);
        var entry = history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW)
                .orElseThrow();
        assertThat(entry.practiceVersion()).isEqualTo(1);
        var criteria = entry.criteria();
        assertThat(criteria).isNotNull();
        assertThat(criteria.asString()).isEqualTo(original.criteria());
    }

    @Test
    void deniedSourceLeavesExplicitlyUnavailableMetadataForContext() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        when(authorization.authorized(any(), any(), any())).thenReturn(false);
        var row = history.forDeveloper(result.getWorkspaceId(), scenarioAccountId, Instant.EPOCH, 10)
                .getFirst();
        assertThat(row.resultId()).isEqualTo(result.getId());
        assertThat(row.copyAuthorityComplete()).isFalse();
        assertThat(row.summary()).isNull();
        assertThat(row.evidence()).isNull();
        assertThat(history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isEmpty();
    }

    @Test
    void returnedJsonCannotMutateTheStoredHistorySnapshot() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var row = history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW)
                .orElseThrow();
        var evidence = row.evidence();
        assertThat(evidence).isNotNull();
        ((ObjectNode) evidence).put("privateMutation", true);
        var unchanged = row.evidence();
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.has("privateMutation")).isFalse();
    }

    @Test
    void shouldRefuseOperatorOnlyProofInAMentorRead() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        when(authorization.authorized(any(), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING)))
                .thenReturn(false);
        assertThat(history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isPresent();
        assertThat(history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isEmpty();
    }

    @Test
    void shouldLimitPrivatePurposeReadsToTheFrozenDeveloperEvenForAnAdmin() {
        var otherDeveloper = data.member(scenario.workspace().getId());
        var other = new ReviewIntegrationData.Scenario(
                scenario.workspace(), otherDeveloper, scenario.practice(), scenario.item());
        var result = data.result(other, ResultOutcome.NOT_MET, Instant.now());
        assertThat(history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isPresent();
        assertThat(history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isEmpty();
        assertThat(history.find(
                        result.getWorkspaceId(),
                        result.getId(),
                        scenarioAccountId,
                        SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isEmpty();
    }

    @Test
    void shouldFilterByTheFrozenDeveloperBeforeLimitingAssignmentHistory() {
        var original = data.result(scenario, ResultOutcome.MET, Instant.now().minusSeconds(60));
        var otherDeveloper = data.member(scenario.workspace().getId());
        var other = new ReviewIntegrationData.Scenario(
                scenario.workspace(), otherDeveloper, scenario.practice(), scenario.item());
        var recent = data.result(other, ResultOutcome.NOT_MET, Instant.now());
        var pending = admissions.pending(scenario, Instant.now(), scenarioAccountId);
        var assignment = new ReviewAssignmentId(
                pending.review().getId(),
                scenario.practice().getId(),
                scenario.item().getId());
        assertThat(history.forItem(
                        scenario.workspace().getId(),
                        scenario.item().getId(),
                        scenario.practice().getId(),
                        1))
                .extracting(ResultHistoryEntry::resultId)
                .containsExactly(recent.getId());
        assertThat(history.forAssignment(scenario.workspace().getId(), assignment, 1))
                .extracting(ResultHistoryEntry::resultId)
                .containsExactly(original.getId());
        assertThat(history.forAssignment(scenario.workspace().getId() + 1, assignment, 1))
                .isEmpty();
    }

    @Test
    void anotherWorkspaceCannotReadTheResultByIdOrItem() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var foreign = data.scenario();
        assertThat(history.find(
                        foreign.workspace().getId(),
                        result.getId(),
                        Objects.requireNonNull(foreign.account().getId()),
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isEmpty();
        assertThat(history.forItem(foreign.workspace().getId(), result.getItemId(), result.getPracticeId(), 20))
                .isEmpty();
    }

    @Test
    void shouldReadTheWorkspaceStandardThatExistedAtTheHistoricalEdge() {
        long workspace = scenario.workspace().getId();
        long practiceId = scenario.practice().getId();
        long originalId =
                Objects.requireNonNull(scenario.practice().getCurrentVersion().getId());
        var edge = Instant.now();
        var definition = DefinitionTestFixtures.definition();
        var changed = new PracticeDefinition(
                definition.name(),
                definition.reviewOn(),
                definition.appliesWhen(),
                definition.about(),
                definition.reads(),
                definition.reviewedBy(),
                "A different review standard.",
                definition.script(),
                definition.scan(),
                definition.lineNotes(),
                definition.guidance());
        var appended = versions.append(scenario.practice(), changed, scenarioAccountId);
        var foreign = data.scenario();
        var ids = List.of(practiceId, foreign.practice().getId());
        assertThat(historicalVersions.findAt(workspace, ids, edge))
                .extracting(v -> v.getId())
                .containsExactly(originalId);
        assertThat(historicalVersions.findAt(workspace, ids, Instant.now()))
                .extracting(v -> v.getId())
                .containsExactly(appended.getId());
    }
}
