package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Collection;

/**
 * Practices-owned erasure port (dependency inversion). Hard-deletes the {@code CONVERSATION_THREAD}-derived
 * observations and feedback that were composed over a set of Slack threads, so a source module that owns those
 * threads (today {@code integration.slack}) can complete a GDPR Art. 17 erasure of the DERIVED practice rows —
 * not just the raw source content — without importing any {@code practices} domain type.
 *
 * <p><strong>Why this exists (no module cycle).</strong> The thread → derived-feedback link is a one-way
 * {@code integration.slack → practices::spi} dependency. The implementation lives INSIDE {@code practices} (an
 * adapter on the practices repositories); the caller injects only this interface. That mirrors how the
 * {@code core.auth::auth-spi} ports ({@code AccountPreferencesQuery} / {@code ResearchParticipationCommand}) are
 * owned by their data module and called from {@code integration.slack} — no reverse edge, so no Spring Modulith
 * cycle forms even though {@code practices} never depends on {@code integration.slack}.
 *
 * <p><strong>Scope (the no-regression contract).</strong> An implementation MUST delete ONLY rows where
 * {@code artifact_type = CONVERSATION_THREAD} AND {@code artifact_id} is one of {@code slackThreadIds} AND that
 * belong to {@code workspaceId}. PR/ISSUE observations and feedback, and rows of any other workspace, MUST be
 * left untouched — the artifact-type, artifact-id, and workspace predicates are all load-bearing.
 */
public interface ConversationFeedbackErasure {
    /**
     * Hard-delete every {@code CONVERSATION_THREAD} observation and feedback unit for {@code workspaceId} whose
     * {@code artifact_id} (the {@code slack_thread} id) is one of {@code slackThreadIds}, cascading to the bound
     * {@code feedback_observation} / {@code feedback_placement} / {@code feedback_reaction} rows via their DB-level
     * {@code ON DELETE CASCADE}. Transactional, idempotent (a thread with no derived rows deletes nothing), and
     * workspace-scoped. An empty collection is a no-op.
     *
     * @param workspaceId    the tenant the derived rows must belong to
     * @param slackThreadIds the {@code slack_thread} ids whose derived rows are being erased
     * @return the number of observation + feedback rows deleted
     */
    int eraseForThreads(long workspaceId, Collection<Long> slackThreadIds);
}
