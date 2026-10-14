package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultSourceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FeedbackHistoryQueryIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    FeedbackHistoryQueryService history;

    @MockitoBean
    ResultHistoryQuery permission;

    @MockitoBean
    ResultSourceAuthorization publicPermission;

    @Test
    void shouldKeepPrivateHistoryWithItsDeveloperAndWorkspace() {
        var privatePiece = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        var publicPiece = piece(FeedbackDestination.ON_WORK, FeedbackState.DELIVERED, AT);
        long otherId =
                Objects.requireNonNull(persistAccount("Another history viewer").getId());
        jdbc.update(
                "INSERT INTO"
                    + " membership(workspace_id,account_id,role,joined_at,review_selected,hidden,public_activity_seen)"
                    + " VALUES (?,?,'MEMBER',now(),false,false,false)",
                workspace.getId(),
                otherId);
        assertThat(history.forItem(workspace.getId(), itemId, otherId, 10))
                .extracting(entry -> entry.feedbackId())
                .containsExactly(publicPiece.getId());
        assertThat(history.forDeveloper(workspace.getId(), otherId, AT.minusSeconds(1), 10))
                .isEmpty();
        assertThat(history.forDeveloper(workspace.getId() + 1, developerId, AT.minusSeconds(1), 10))
                .isEmpty();
        assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 10))
                .extracting(entry -> entry.feedbackId())
                .containsExactlyInAnyOrder(privatePiece.getId(), publicPiece.getId());
    }

    @Test
    void shouldExcludeHiddenSuspendedAndDepartedMembersFromHistory() {
        var row = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 10))
                .extracting(entry -> entry.feedbackId())
                .containsExactly(row.getId());
        jdbc.update(
                "UPDATE membership SET hidden=true WHERE workspace_id=? AND account_id=?",
                workspace.getId(),
                developerId);
        assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 10))
                .isEmpty();
        assertThat(history.forItem(workspace.getId(), itemId, developerId, 10)).isEmpty();
        jdbc.update(
                "UPDATE membership SET hidden=false WHERE workspace_id=? AND account_id=?",
                workspace.getId(),
                developerId);
        try {
            jdbc.update("UPDATE account SET status='SUSPENDED' WHERE id=?", developerId);
            assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 10))
                    .isEmpty();
            assertThat(history.forItem(workspace.getId(), itemId, developerId, 10))
                    .isEmpty();
        } finally {
            jdbc.update("UPDATE account SET status='ACTIVE' WHERE id=?", developerId);
        }
        assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 10))
                .extracting(entry -> entry.feedbackId())
                .containsExactly(row.getId());
        jdbc.update(
                "UPDATE membership SET left_at=now() WHERE workspace_id=? AND account_id=?",
                workspace.getId(),
                developerId);
        assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 10))
                .isEmpty();
        assertThat(history.forItem(workspace.getId(), itemId, developerId, 10)).isEmpty();
    }

    @Test
    void shouldRedactUnavailableEvidenceWhileRetainingTheRecordedDeliveryState() {
        var row = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.DELIVERED, AT);
        var proof = result();
        bind(row, proof);
        when(permission.find(workspace.getId(), proof.getId(), developerId, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(Optional.empty());
        var unavailable = history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 1)
                .getFirst();
        assertThat(unavailable.body()).isNull();
        assertThat(unavailable.state()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(unavailable.deliveredAt()).isEqualTo(AT);
        var retained = mock(ResultHistoryEntry.class);
        when(retained.copyAuthorityComplete()).thenReturn(true);
        when(retained.resultId()).thenReturn(proof.getId());
        when(retained.sourceJobIds()).thenReturn(List.of());
        when(retained.copiedIdentities()).thenReturn(List.of());
        when(retained.repositoryIds()).thenReturn(List.of());
        when(permission.find(workspace.getId(), proof.getId(), developerId, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(Optional.of(retained));
        assertThat(history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 1)
                        .getFirst()
                        .body())
                .isEqualTo(row.getBody());
    }

    @Test
    void shouldExposeAnAuthorizedReadyBriefWithoutInventingAThreadReceipt() {
        var row = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        var proof = result();
        bind(row, proof);
        authorize(proof);
        var entry = history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 1)
                .getFirst();
        assertThat(entry.body()).isEqualTo(row.getBody());
        assertThat(entry.threadId()).isNull();
        assertThat(entry.deliveredAt()).isNull();
        assertThat(entry.copyAuthorityComplete()).isTrue();
    }

    @Test
    void shouldReturnOnlyConfirmedWordsWhenASiblingRemainsUnknown() {
        var row = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, AT);
        var proof = result();
        bind(row, proof);
        authorize(proof);
        summary(row, FeedbackCopyState.WRITE_STARTED);
        var copyId = FeedbackIds.copy(row.getId(), FeedbackCopyKind.FILE_NOTE, 1);
        copies.saveAndFlush(FeedbackCopy.builder()
                .id(copyId)
                .workspaceId(workspace.getId())
                .feedbackId(row.getId())
                .kind(FeedbackCopyKind.FILE_NOTE)
                .position(1)
                .state(FeedbackCopyState.POSTED)
                .body("Cover the failure case.")
                .path("src/A.java")
                .writeKey(copyId.toString())
                .writeMarker(FeedbackIds.writeMarker(copyId))
                .writeStartedAt(AT)
                .postedAt(AT)
                .externalRef("confirmed-note")
                .createdAt(AT)
                .build());
        var entry = history.forItem(workspace.getId(), itemId, developerId, 1).getFirst();
        assertThat(entry.state()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(entry.body()).isEqualTo("Cover the failure case.");
        var publicHistory = Objects.requireNonNull(entry.publicHistory());
        assertThat(publicHistory.path("body").isNull()).isTrue();
        assertThat(publicHistory.path("copies").size()).isEqualTo(1);
        assertThat(publicHistory.path("copies").get(0).path("externalRef").asString())
                .isEqualTo("confirmed-note");
    }

    @Test
    void shouldReturnTheActualShownConversationWordsAndThread() {
        var row = piece(FeedbackDestination.CONVERSATION, FeedbackState.DELIVERED, AT);
        var proof = result();
        bind(row, proof);
        authorize(proof);
        UUID thread = UUID.randomUUID(), message = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO chat_thread(id,workspace_id,account_id,created_at,surface) VALUES"
                        + " (?,?,?,now(),'WEB')",
                thread,
                workspace.getId(),
                developerId);
        jdbc.update(
                "INSERT INTO"
                        + " chat_message(id,thread_id,workspace_id,role,status,created_at,parts,version)"
                        + " VALUES (?,?,?,'ASSISTANT','completed',now(),?::jsonb,0)",
                message,
                thread,
                workspace.getId(),
                "[{\"type\":\"text\",\"text\":\"Try a focused test.\"}]");
        UUID id = FeedbackIds.copy(row.getId(), FeedbackCopyKind.CHAT_TURN, 0);
        copies.saveAndFlush(FeedbackCopy.builder()
                .id(id)
                .workspaceId(workspace.getId())
                .feedbackId(row.getId())
                .kind(FeedbackCopyKind.CHAT_TURN)
                .state(FeedbackCopyState.POSTED)
                .body("Try a focused test.")
                .writeKey(id.toString())
                .writeStartedAt(AT)
                .postedAt(AT)
                .chatMessageId(message)
                .createdAt(AT)
                .build());
        var entry = history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 1)
                .getFirst();
        assertThat(entry.body()).isEqualTo("Try a focused test.").isNotEqualTo(row.getBody());
        assertThat(entry.threadId()).isEqualTo(thread);
        assertThat(entry.publicHistory()).isNull();
    }

    @Test
    void shouldExposeConfirmedPublicCopyWordsToAPeerWithoutExposingPrivateResultWords() {
        var row = piece(FeedbackDestination.ON_WORK, FeedbackState.DELIVERED, AT);
        var proof = result();
        bind(row, proof);
        summary(row, FeedbackCopyState.POSTED);
        authorize(proof);
        long peerId =
                Objects.requireNonNull(persistAccount("Public feedback viewer").getId());
        jdbc.update(
                "INSERT INTO"
                    + " membership(workspace_id,account_id,role,joined_at,review_selected,hidden,public_activity_seen)"
                    + " VALUES (?,?,'MEMBER',now(),false,false,false)",
                workspace.getId(),
                peerId);
        var entry = history.forItem(workspace.getId(), itemId, peerId, 1).getFirst();
        assertThat(entry.body()).isEqualTo(row.getBody());
        assertThat(entry.copyAuthorityComplete()).isTrue();
        assertThat(Objects.requireNonNull(entry.publicHistory())
                        .path("copies")
                        .get(0)
                        .path("body")
                        .asString())
                .isEqualTo(row.getBody());
        verify(permission, never())
                .find(workspace.getId(), proof.getId(), peerId, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        when(publicPermission.authorized(any(), any(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenReturn(false);
        assertThat(history.forItem(workspace.getId(), itemId, peerId, 1)
                        .getFirst()
                        .body())
                .isNull();
    }

    @Test
    void shouldUseMentoringPermissionForPrivateFeedbackHistory() {
        var row = piece(FeedbackDestination.CONVERSATION, FeedbackState.READY, null);
        var proof = result();
        bind(row, proof);
        authorize(proof);
        when(permission.find(workspace.getId(), proof.getId(), developerId, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(Optional.empty());
        var entry = history.forDeveloper(workspace.getId(), developerId, AT.minusSeconds(1), 1)
                .getFirst();
        assertThat(entry.body()).isNull();
        assertThat(entry.state()).isEqualTo(FeedbackState.READY);
        verify(permission, never())
                .find(workspace.getId(), proof.getId(), developerId, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
    }

    private void authorize(Result proof) {
        var custody = new HistoryCustody(true, List.of(), List.of(), List.of());
        when(publicPermission.custody(argThat(row -> row != null && row.getId().equals(proof.getId()))))
                .thenReturn(custody);
        for (var purpose :
                List.of(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY, SourceUsePurpose.CONVERSATIONAL_MENTORING))
            when(publicPermission.authorized(
                            argThat(row -> row != null && row.getId().equals(proof.getId())), eq(custody), eq(purpose)))
                    .thenReturn(true);
        var retained = mock(ResultHistoryEntry.class);
        when(retained.copyAuthorityComplete()).thenReturn(true);
        when(retained.resultId()).thenReturn(proof.getId());
        when(retained.practiceSlug()).thenReturn("regression-tests");
        when(permission.find(workspace.getId(), proof.getId(), developerId, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(Optional.of(retained));
        when(permission.find(
                        workspace.getId(), proof.getId(), developerId, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Optional.of(retained));
    }
}
