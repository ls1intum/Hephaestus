package de.tum.cit.aet.hephaestus.agent.conversation;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;

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

    private ChatSignals() {}
}
