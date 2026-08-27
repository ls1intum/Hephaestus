package de.tum.cit.aet.hephaestus.agent.conversation;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRevision;

/**
 * The signal vocabulary of a chat conversation thread.
 *
 * <p>Lives beside {@link ConversationThreadProjection} rather than in a vendor package because the
 * artifact is assembled here: a thread is a projection this module builds out of ingested messages, and
 * {@code integration.slack} owns the messages, not the thread. Putting the vocabulary in Slack would put
 * a vendor's name behind an identifier a second messaging integration is meant to reuse.
 */
public final class ChatSignals {

    public static final ArtifactKind CONVERSATION_THREAD = ArtifactKind.of("chat.conversation_thread");

    /**
     * A discussion has stopped and is worth reading as a whole.
     *
     * <p>The one occasion a conversation practice can be reviewed on, and it is a fact about the thread
     * rather than about any message in it — which is why no ingested event raises it and a scheduler
     * decides it instead.
     */
    public static final SignalName CONVERSATION_THREAD_SETTLED = SignalName.of("chat.conversation_thread.settled");

    /**
     * A thread has no commits and no version of its own, so an occurrence is identified by what the
     * thread was at the moment it settled: where it starts, where it currently ends, and how much was
     * said.
     */
    public static final RevisionScheme REVISION_SCHEME = RevisionScheme.CONTENT_DIGEST;

    private ChatSignals() {}

    /**
     * The ledger identity of one settled-thread occurrence. The turn count is part of the digest, not
     * decoration: without it, a thread that grew by replies later all tombstoned would come back with the
     * same root/last {@code ts} and be silently deduplicated against an occurrence that measured different
     * content.
     *
     * <p>Deliberately <em>not</em> the growth gate: this moves on a single new turn, while the scheduler
     * requires two — swapping one for the other would quietly raise how often conversations are reviewed.
     *
     * @param threadId the {@code slack_thread} row id, which is what the delivery and trace surfaces
     *     already call this artifact
     */
    public static SignalKey threadSettledKey(
            long workspaceId, long threadId, String threadTs, String lastTs, long liveTurnCount) {
        return new SignalKey(
                workspaceId,
                threadId,
                CONVERSATION_THREAD_SETTLED,
                SignalRevision.ofContentDigest(threadTs, lastTs, Long.toString(liveTurnCount)));
    }
}
