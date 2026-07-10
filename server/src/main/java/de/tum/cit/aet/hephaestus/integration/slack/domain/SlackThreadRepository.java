package de.tum.cit.aet.hephaestus.integration.slack.domain;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scoped access to Slack thread aggregates. Every finder carries the {@code workspace_id} predicate the tenancy
 * {@code StatementInspector} requires.
 */
public interface SlackThreadRepository extends JpaRepository<SlackThread, Long> {
    Optional<SlackThread> findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(
        Long workspaceId,
        String slackChannelId,
        String slackThreadTs
    );

    /**
     * Idempotent thread-aggregate upsert on a freshly ingested message. Creates the
     * {@code (workspace_id, slack_channel_id, slack_thread_ts)} row on first sight and, on the unique conflict,
     * advances the {@code first_ts}/{@code last_ts} window (Slack {@code ts} strings sort lexicographically —
     * fixed {@code <10-digit>.<6-digit>} format), bumps {@code message_count}, and unions the author's resolved
     * member id into {@code participant_member_ids} (the GIN-indexed {@code bigint[]} the participant firewall
     * matches with {@code = ANY(...)}). {@code participant_member_ids} is array-typed and mapped on the entity via
     * {@code @JdbcTypeCode(SqlTypes.ARRAY)}; this write is still a native {@code @Modifying} statement rather than an
     * entity save because the conflict-resolving merge logic (window advance, count bump, array union) has no
     * Spring Data derived form.
     *
     * <p>The caller passes {@code slackThreadTs := thread_ts} for a reply and {@code := ts} for a thread root, so
     * a root and its replies collapse onto one aggregate row. {@code authorMemberId} is nullable (an unlinked
     * Slack author stamps no member id); the union is a no-op in that case. INSERTs/ON CONFLICT updates carry the
     * explicit {@code workspace_id} in the row, so no tenant leak is possible.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO slack_thread (workspace_id, slack_channel_id, slack_thread_ts, first_ts, last_ts, message_count, participant_member_ids, created_at)
        VALUES (
            :workspaceId, :slackChannelId, :slackThreadTs, :slackTs, :slackTs, 1,
            CASE WHEN :authorMemberId IS NULL THEN '{}'::bigint[] ELSE ARRAY[CAST(:authorMemberId AS bigint)] END,
            now()
        )
        ON CONFLICT (workspace_id, slack_channel_id, slack_thread_ts) DO UPDATE SET
            first_ts = CASE
                WHEN slack_thread.first_ts IS NULL OR EXCLUDED.first_ts < slack_thread.first_ts
                THEN EXCLUDED.first_ts ELSE slack_thread.first_ts END,
            last_ts = CASE
                WHEN slack_thread.last_ts IS NULL OR EXCLUDED.last_ts > slack_thread.last_ts
                THEN EXCLUDED.last_ts ELSE slack_thread.last_ts END,
            message_count = slack_thread.message_count + 1,
            participant_member_ids = CASE
                WHEN :authorMemberId IS NULL OR CAST(:authorMemberId AS bigint) = ANY(slack_thread.participant_member_ids)
                THEN slack_thread.participant_member_ids
                ELSE array_append(slack_thread.participant_member_ids, CAST(:authorMemberId AS bigint)) END
        """,
        nativeQuery = true
    )
    void upsertOnMessage(
        @Param("workspaceId") long workspaceId,
        @Param("slackChannelId") String slackChannelId,
        @Param("slackThreadTs") String slackThreadTs,
        @Param("slackTs") String slackTs,
        @Param("authorMemberId") @Nullable Long authorMemberId
    );

    /**
     * The ids of every thread aggregate on one channel — collected on channel erasure so the derived
     * {@code CONVERSATION_THREAD} observations/feedback (keyed by these {@code slack_thread} ids as
     * {@code artifact_id}) can be hard-deleted through the practices erasure port before the aggregates themselves
     * are dropped.
     */
    @Query("SELECT t.id FROM SlackThread t WHERE t.workspaceId = :workspaceId AND t.slackChannelId = :slackChannelId")
    List<Long> findIdsByWorkspaceIdAndSlackChannelId(
        @Param("workspaceId") Long workspaceId,
        @Param("slackChannelId") String slackChannelId
    );

    /** Workspace purge: delete every thread aggregate for one workspace. */
    long deleteByWorkspaceId(Long workspaceId);

    /**
     * Channel erasure: delete every thread aggregate of one channel promptly when its consent is withdrawn (the
     * aggregates hold the {@code participant_member_ids} personal data and are the artifact the derived CONVERSATION
     * feedback points at). Idempotent (returns 0 when the channel had no threads).
     */
    long deleteByWorkspaceIdAndSlackChannelId(Long workspaceId, String slackChannelId);

    /**
     * Retention sweep: the ids of every thread aggregate for a workspace that has gone cold — its most recent
     * activity ({@code last_ts}, the newest message in the thread) is strictly older than {@code cutoffTs}.
     * Because {@code last_ts} is the maximum message {@code ts} in the thread, "last_ts older than the cutoff" is
     * exactly "every message in the thread is older than the cutoff", so the whole thread (raw messages, the
     * {@code participant_member_ids} aggregate, and the derived CONVERSATION feedback) can be erased together.
     *
     * <p>{@code cutoffTs} is the retention cutoff rendered as a Slack {@code ts} string
     * ({@code <10-digit-epoch-seconds>.000000}); the comparison is lexicographic, which equals numeric ordering for
     * the fixed {@code <10-digit>.<6-digit>} Slack {@code ts} format (the same invariant {@link #upsertOnMessage}
     * relies on). {@code last_ts} is always populated on ingest, so a NULL guard is unnecessary.
     */
    @Query(
        "SELECT t.id FROM SlackThread t WHERE t.workspaceId = :workspaceId AND t.lastTs IS NOT NULL AND t.lastTs < :cutoffTs"
    )
    List<Long> findAgedThreadIds(@Param("workspaceId") Long workspaceId, @Param("cutoffTs") String cutoffTs);

    /**
     * Retention sweep: drop a set of aged thread aggregates for a workspace after their derived CONVERSATION feedback
     * has already been erased through the practices port. Idempotent (0 when the id set is empty / already gone).
     * Callers guard an empty collection.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SlackThread t WHERE t.workspaceId = :workspaceId AND t.id IN :ids")
    int deleteByWorkspaceIdAndIdIn(
        @Param("workspaceId") Long workspaceId,
        @Param("ids") java.util.Collection<Long> ids
    );

    /**
     * Person erasure (opt-out / account hard-delete): drop one member's id out of every thread's
     * {@code participant_member_ids} for a workspace via {@code array_remove}. The append-only participant array
     * otherwise keeps a person's id (and, on id reuse, their thread visibility) after they leave — this prunes it.
     * The {@code :memberId = ANY(participant_member_ids)} guard narrows the write set to rows that actually contain
     * the member (the GIN index on the array serves it), so unaffected threads are not rewritten. Native (the
     * {@code array_remove} merge has no Spring Data derived form) and workspace-scoped. Idempotent
     * (0 when no thread references the member).
     *
     * @return the number of thread aggregates pruned
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE slack_thread SET participant_member_ids = array_remove(participant_member_ids, :memberId) " +
            "WHERE workspace_id = :workspaceId AND :memberId = ANY(participant_member_ids)",
        nativeQuery = true
    )
    int pruneParticipant(@Param("workspaceId") long workspaceId, @Param("memberId") long memberId);

    /** Scoped row count for a workspace. */
    long countByWorkspaceId(Long workspaceId);

    /**
     * Agent-owned {@code ConversationCandidateSource} SPI: threads on an {@code ACTIVE} channel that have grown
     * past their review watermark and have at least {@code minMessageCount} messages, oldest {@code last_ts}
     * first. The {@code participant_member_ids bigint[]} is selected as a plain mapped field (no raw JDBC array
     * decode needed — {@link SlackThread#getParticipantMemberIds()} rides Hibernate's own
     * {@code @JdbcTypeCode(SqlTypes.ARRAY)} marshalling). Rows come back as {@code Object[]} rather than a JPQL
     * constructor expression into the agent's {@link ConversationThreadCandidate} record: that record's
     * {@code workspaceId}/{@code threadId} components are primitive {@code long}, and Hibernate's "SELECT new"
     * constructor resolution is not guaranteed to widen a selected (boxed) {@code Long} attribute into a primitive
     * constructor parameter — {@link SlackConversationCandidateSource#toCandidate} does the explicit unboxing
     * instead (same {@code Object[]} idiom as
     * {@code MentorContextQueryRepository#findFirstUserMessagePartsByThreadIds}). Columns: {@code workspace_id},
     * {@code id}, {@code slack_channel_id}, {@code slack_thread_ts}, {@code last_ts}, {@code last_reviewed_ts},
     * {@code participant_member_ids}.
     *
     * <p>Deliberately unscoped — cross-workspace by design; each returned candidate carries its own
     * {@code workspaceId}. The join predicate ({@code c.workspaceId = t.workspaceId}) still mentions
     * {@code workspace_id} in the emitted SQL, so the runtime tenancy inspector passes without a bypass; the
     * compile-time repository-scoping check is satisfied via the explicit allowlist in
     * {@code SlackIntegrationArchitectureTest} (mirroring {@link SlackMessageRepository#findDistinctWorkspaceIds}).
     * The caller ({@code SlackConversationCandidateSource.settledCandidates}, and above it the agent's
     * {@code ConversationThreadTriggerScheduler}) is itself {@code @WorkspaceAgnostic} for exactly this reason.
     */
    @Query(
        """
        SELECT t.workspaceId, t.id, t.slackChannelId, t.slackThreadTs, t.lastTs, t.lastReviewedTs, t.participantMemberIds
        FROM SlackThread t
        JOIN SlackMonitoredChannel c ON c.workspaceId = t.workspaceId AND c.slackChannelId = t.slackChannelId
        WHERE c.consentState = de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState.ACTIVE
          AND t.lastTs IS NOT NULL
          AND (t.lastReviewedTs IS NULL OR t.lastTs > t.lastReviewedTs)
          AND t.messageCount >= :minMessageCount
        ORDER BY t.lastTs ASC
        """
    )
    List<Object[]> findSettledCandidateRows(@Param("minMessageCount") int minMessageCount);

    /**
     * Agent-owned {@code ConversationSourceLiveness} SPI: the subset of {@code threadIds} whose source channel
     * consent is still {@code ACTIVE} in this workspace — the fail-closed allow-set a
     * {@code CONVERSATION_THREAD}-derived row must belong to before it may reach the mentor. Callers guard an
     * empty {@code threadIds} (a native {@code IN ()} — or here, JPQL {@code IN :threadIds} — would otherwise be
     * evaluated against nothing productively).
     */
    @Query(
        "SELECT t.id FROM SlackThread t " +
            "JOIN SlackMonitoredChannel c ON c.workspaceId = t.workspaceId AND c.slackChannelId = t.slackChannelId " +
            "WHERE t.workspaceId = :workspaceId AND t.id IN :threadIds " +
            "AND c.consentState = de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState.ACTIVE"
    )
    List<Long> findActiveThreadIds(
        @Param("workspaceId") long workspaceId,
        @Param("threadIds") Collection<Long> threadIds
    );

    /**
     * Agent-owned {@code ConversationThreadProjection} SPI (participant-firewalled thread listing): threads in the
     * workspace whose channel consent is {@code ACTIVE} and whose participant set contains {@code audienceMemberId},
     * newest-active first, capped at {@code limit}. Native — Postgres {@code = ANY(participant_member_ids)} GIN
     * array-membership has no portable JPQL form (the array is a genuine Postgres type, not a to-many relationship
     * JPA can traverse). Columns: {@code slack_channel_id}, {@code channel_name} (nullable), {@code slack_thread_ts},
     * {@code message_count} — unpacked by the caller into its own row type.
     */
    @Query(
        value = """
        SELECT t.slack_channel_id, c.channel_name, t.slack_thread_ts, t.message_count
        FROM slack_thread t
        JOIN slack_monitored_channel c ON c.workspace_id = t.workspace_id AND c.slack_channel_id = t.slack_channel_id
        WHERE t.workspace_id = :workspaceId
          AND c.consent_state = 'ACTIVE'
          AND :audienceMemberId = ANY(t.participant_member_ids)
        ORDER BY t.last_ts DESC NULLS LAST
        LIMIT :limit
        """,
        nativeQuery = true
    )
    List<Object[]> findParticipatingThreadRows(
        @Param("workspaceId") long workspaceId,
        @Param("audienceMemberId") long audienceMemberId,
        @Param("limit") int limit
    );

    /**
     * Agent-owned {@code ConversationCandidateSource} SPI: advance the thread's {@code last_reviewed_ts} watermark
     * to {@code lastTs}, workspace-pinned. Scoped {@code @Modifying} JPQL update (the entity is not loaded into
     * the persistence context first — the caller has only the thread id from a prior scan).
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE SlackThread t SET t.lastReviewedTs = :lastTs WHERE t.workspaceId = :workspaceId AND t.id = :threadId"
    )
    int advanceReviewWatermark(
        @Param("workspaceId") long workspaceId,
        @Param("threadId") long threadId,
        @Param("lastTs") String lastTs
    );
}
