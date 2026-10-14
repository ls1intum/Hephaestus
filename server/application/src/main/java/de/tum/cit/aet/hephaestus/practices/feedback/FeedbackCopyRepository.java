package de.tum.cit.aet.hephaestus.practices.feedback;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface FeedbackCopyRepository extends JpaRepository<FeedbackCopy, UUID> {

    List<FeedbackCopy> findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(long workspaceId, UUID feedbackId);

    Optional<FeedbackCopy> findByIdAndWorkspaceId(UUID id, long workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FeedbackCopy c WHERE c.feedbackId = :feedbackId AND c.workspaceId ="
            + " :workspaceId ORDER BY c.id")
    List<FeedbackCopy> lockByFeedbackId(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query(value = """
        SELECT EXISTS(SELECT 1 FROM feedback_copy WHERE workspace_id = :workspaceId
         AND feedback_id = :feedbackId AND (write_started_at IS NOT NULL OR posted_at IS NOT NULL
           OR external_ref IS NOT NULL OR state IN ('WRITE_STARTED','POSTED','RETRACTED')))
        """, nativeQuery = true)
    boolean hasWriteFence(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state = 'WRITE_STARTED', write_started_at = COALESCE(write_started_at,:startedAt), stop_reason='DELIVERY_UNCONFIRMED',last_error=NULL,readback='UNKNOWN'
         WHERE workspace_id = :workspaceId AND feedback_id = :feedbackId AND id = :copyId
           AND state = 'PLANNED' AND (write_started_at IS NULL OR readback = 'ABSENT')
           AND external_ref IS NULL AND posted_at IS NULL AND write_key IS NOT NULL
        """, nativeQuery = true)
    int beginWrite(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("startedAt") Instant startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state = 'POSTED', posted_at = :postedAt,
            external_ref = :externalRef, external_url = :externalUrl, kind = :kind,
            stop_reason = NULL, last_error = NULL, readback=NULL
         WHERE workspace_id = :workspaceId AND feedback_id = :feedbackId AND id = :copyId
           AND state = 'WRITE_STARTED' AND write_started_at IS NOT NULL
           AND external_ref IS NULL AND posted_at IS NULL
        """, nativeQuery = true)
    int confirmPosted(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("postedAt") Instant postedAt,
            @Param("externalRef") @Nullable String externalRef,
            @Param("externalUrl") @Nullable String externalUrl,
            @Param("kind") String kind);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state = 'FAILED', stop_reason = NULL, last_error = :error, readback='ABSENT'
         WHERE workspace_id = :workspaceId AND feedback_id = :feedbackId AND id = :copyId
           AND state = 'WRITE_STARTED' AND external_ref IS NULL AND posted_at IS NULL
        """, nativeQuery = true)
    int recordAbsent(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("error") String error);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='WITHHELD',stop_reason=:reason
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND state='PLANNED' AND (write_started_at IS NULL OR readback = 'ABSENT')
        """, nativeQuery = true)
    int withholdPlanned(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='PLANNED',stop_reason=NULL,last_error=NULL
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND state='WITHHELD' AND stop_reason='RESULT_MARKED_INCORRECT' AND (write_started_at IS NULL OR readback='ABSENT') AND posted_at IS NULL AND external_ref IS NULL AND chat_message_id IS NULL
        """, nativeQuery = true)
    int restoreUnsent(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='PLANNED' WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId
          AND state='FAILED' AND external_ref IS NULL AND posted_at IS NULL
          AND (write_started_at IS NULL OR readback = 'ABSENT')
        """, nativeQuery = true)
    int retryAbsent(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET last_error=:error,stop_reason=:reason,readback=:readback
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND id=:copyId
        """, nativeQuery = true)
    int recordError(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("reason") @Nullable String reason,
            @Param("readback") @Nullable String readback,
            @Param("error") @Nullable String error);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state=:state,last_error=NULL,stop_reason=NULL,readback=NULL
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND id=:copyId
          AND state IN ('POSTED','RETRACTED') AND posted_at IS NOT NULL
        """, nativeQuery = true)
    int confirmCorrection(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("state") String state);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='WITHHELD',stop_reason=:reason WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId
          AND id=:copyId AND state='PLANNED' AND (write_started_at IS NULL OR readback = 'ABSENT')
        """, nativeQuery = true)
    int withholdCopy(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='FAILED',readback='ABSENT',last_error='No provider request was admitted.'
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND state='PLANNED' AND (write_started_at IS NULL OR readback = 'ABSENT')
        """, nativeQuery = true)
    int failUnstarted(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query(value = """
        SELECT EXISTS(SELECT 1 FROM chat_message m JOIN chat_thread t
          ON t.id=m.thread_id AND t.workspace_id=m.workspace_id
          WHERE m.id=:messageId AND m.workspace_id=:workspaceId AND t.account_id=:developerId AND m.role='ASSISTANT')
        """, nativeQuery = true)
    boolean ownsConversationMessage(
            @Param("workspaceId") long workspaceId,
            @Param("developerId") long developerId,
            @Param("messageId") UUID messageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='WRITE_STARTED',write_started_at=:at,chat_message_id=:messageId,
          stop_reason='DELIVERY_UNCONFIRMED',last_error=NULL,readback='UNKNOWN'
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND id=:copyId AND kind='CHAT_TURN'
          AND state='PLANNED' AND write_started_at IS NULL AND posted_at IS NULL AND external_ref IS NULL
          AND chat_message_id IS NULL AND body IS NULL AND write_key IS NOT NULL
        """, nativeQuery = true)
    int claimConversation(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("messageId") UUID messageId,
            @Param("at") Instant at);

    @Query(value = """
        SELECT string_agg(part.value->>'text','' ORDER BY part.position)
        FROM chat_message m JOIN chat_thread t ON t.id=m.thread_id AND t.workspace_id=m.workspace_id
        JOIN LATERAL jsonb_array_elements(m.parts) WITH ORDINALITY AS part(value,position) ON true
        WHERE m.id=:messageId AND m.workspace_id=:workspaceId AND t.account_id=:developerId
          AND m.role='ASSISTANT' AND m.status='completed' AND part.value->>'type'='text' GROUP BY m.id
        """, nativeQuery = true)
    Optional<String> completedConversationText(
            @Param("workspaceId") long workspaceId,
            @Param("developerId") long developerId,
            @Param("messageId") UUID messageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state='POSTED',posted_at=:at,body=:body,stop_reason=NULL,last_error=NULL,readback=NULL
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND id=:copyId AND kind='CHAT_TURN'
          AND chat_message_id=:messageId AND write_started_at IS NOT NULL AND posted_at IS NULL
          AND state IN ('WRITE_STARTED','FAILED') AND body IS NULL
        """, nativeQuery = true)
    int recordConversationDelivered(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("messageId") UUID messageId,
            @Param("body") String body,
            @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE feedback_copy SET state=:state,stop_reason=:reason,last_error=:detail,readback=:readback
        WHERE workspace_id=:workspaceId AND feedback_id=:feedbackId AND id=:copyId AND kind='CHAT_TURN'
          AND chat_message_id=:messageId AND write_started_at IS NOT NULL AND posted_at IS NULL
          AND state IN ('WRITE_STARTED','FAILED')
        """, nativeQuery = true)
    int recordConversationOutcome(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("copyId") UUID copyId,
            @Param("messageId") UUID messageId,
            @Param("state") String state,
            @Param("reason") @Nullable String reason,
            @Param("readback") String readback,
            @Param("detail") @Nullable String detail);

    @Query(value = """
        SELECT EXISTS(SELECT 1 FROM feedback_copy newer JOIN feedback latest ON latest.id=newer.feedback_id AND latest.workspace_id=newer.workspace_id
          JOIN feedback original ON original.id=:feedbackId AND original.workspace_id=:workspaceId
          WHERE newer.workspace_id=:workspaceId AND newer.feedback_id<>original.id AND newer.kind='SUMMARY'
            AND newer.external_ref=:externalRef AND newer.posted_at IS NOT NULL AND latest.item_id=original.item_id
            AND (latest.created_at>original.created_at OR (latest.created_at=original.created_at AND latest.id::text>original.id::text)))
        """, nativeQuery = true)
    boolean hasNewerSummaryReceipt(
            @Param("workspaceId") long workspaceId,
            @Param("feedbackId") UUID feedbackId,
            @Param("externalRef") String externalRef);
}
