package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.review.ReviewTestData;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class ResultVisibilityPolicyTest {
    private final EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);
    private final ResultMarkRepository marks = mock(ResultMarkRepository.class);
    private final PracticeRepository practices = mock(PracticeRepository.class);
    private final PracticeVersionRepository versions = mock(PracticeVersionRepository.class);
    private final ResultVisibilityPolicy policy = new ResultVisibilityPolicy(authorization, marks, practices, versions);
    private final Practice practice = ReviewTestData.practice(1, 1, "test-practice");

    @BeforeEach
    void catalog() {
        when(practices.findByWorkspaceId(1L)).thenReturn(List.of(practice));
        when(versions.findAllById(ArgumentMatchers.any())).thenReturn(List.of(practice.getCurrentVersion()));
        when(marks.findByWorkspaceIdAndResultIdInAndRemovedAtIsNull(ArgumentMatchers.eq(1L), ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    @Test
    void forwardsTheActualSourceAuthorizationForCurrentProof() {
        var r = result();
        when(authorization.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Set.of(r.getId()));
        assertThat(policy.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .containsExactly(r.getId());
    }

    @Test
    void endedProofNeverCountsAsCurrent() {
        var r = result();
        r.end(ResultEndReason.REPLACED, r.getRecordedAt().plusSeconds(1));
        assertThat(policy.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isEmpty();
    }

    @Test
    void archivedProofIsUnverifiableForNewClaims() {
        var r = result();
        r.setEvidenceVersion((short) 0);
        assertThat(ResultVisibilityPolicy.currentness(r, practice.getCurrentVersion(), practice.getCurrentVersion()))
                .isEqualTo(Currentness.UNVERIFIABLE);
        assertThat(policy.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isEmpty();
    }

    @Test
    void aChangedStandardMakesProofStale() {
        var r = result();
        var old = new PracticeVersion(
                practice, 1, new JsonMapper().valueToTree(DefinitionTestFixtures.definition()), null);
        ReflectionTestUtils.setField(old, "reviewDigest", "v6:" + "b".repeat(64));
        assertThat(ResultVisibilityPolicy.currentness(r, old, practice.getCurrentVersion()))
                .isEqualTo(Currentness.STALE);
    }

    @Test
    void anIncorrectMarkWithholdsCurrentProof() {
        var r = result();
        var mark = ResultMark.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .resultId(r.getId())
                .kind(ResultMarkKind.INCORRECT)
                .reason("Incorrect attribution")
                .createdAt(r.getRecordedAt())
                .build();
        when(marks.findByWorkspaceIdAndResultIdInAndRemovedAtIsNull(1L, List.of(r.getId())))
                .thenReturn(List.of(mark));
        assertThat(policy.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isEmpty();
    }

    @Test
    void acceptanceDoesNotEraseAuthorizedProof() {
        var r = result();
        var mark = ResultMark.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .resultId(r.getId())
                .kind(ResultMarkKind.ACCEPTED)
                .reason("Recorded and accepted")
                .createdAt(r.getRecordedAt())
                .build();
        when(marks.findByWorkspaceIdAndResultIdInAndRemovedAtIsNull(1L, List.of(r.getId())))
                .thenReturn(List.of(mark));
        when(authorization.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Set.of(r.getId()));
        assertThat(policy.permitsAll(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .containsExactly(r.getId());
    }

    @Test
    void foreignRowsAreNotSentForSourceAuthorization() {
        var r = result();
        r.setWorkspaceId(2);
        assertThat(policy.permitsHistory(1L, List.of(r), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isEmpty();
    }

    private Result result() {
        return Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .reviewId(UUID.randomUUID())
                .practiceId(1)
                .practiceVersionId(practice.getCurrentVersion().getId())
                .itemId(1)
                .outcome(ResultOutcome.MET)
                .recordedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
    }
}
