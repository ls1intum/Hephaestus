package de.tum.cit.aet.hephaestus.core.runtime.hub.session;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionClose;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOutput;

/**
 * Hook for worker-originated session frames. Injected as {@code Optional} so the WSS handler
 * compiles without a bridge (cold-start smoke tests, boot-without-mentor monolith).
 */
public interface HubSessionInbox {
    void onSessionOutput(SessionOutput frame);

    void onSessionClose(SessionClose frame);
}
