package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for multi-replica mentor deployments.
 *
 * <p>Live mentor sessions are pinned to one app-server replica because the
 * {@code InteractiveSandboxRegistry} is in-process: the {@code AttachedSandbox} owning a
 * thread's stdin/stdout, the per-turn {@code MentorTurnLock}, and the in-flight {@code SseEmitter}
 * all live in JVM memory. Routing a follow-up turn to a different replica deadlocks (the
 * sandbox is on a peer) and the {@code ux_chat_message_in_flight} index is the only durable
 * backstop against poisoned threads.
 *
 * <p>The proper fix is sticky sessions per {@code (workspaceId, threadId)} — tracked in #1077.
 * Until then, scaling beyond one replica without sticky routing is a footgun, so we refuse to
 * start: ops must either set {@code hephaestus.mentor.replica-count=1} or opt-in to
 * {@code hephaestus.mentor.replica-affinity.sticky=true} once the infra change lands.
 */
@Component
@WorkspaceAgnostic("Cluster-topology guard — not tenant-scoped")
public class MentorReplicaAffinityCheck {

    private static final Logger log = LoggerFactory.getLogger(MentorReplicaAffinityCheck.class);

    private final int replicaCount;
    private final boolean stickyAffinity;

    public MentorReplicaAffinityCheck(
        @Value("${hephaestus.mentor.replica-count:1}") int replicaCount,
        @Value("${hephaestus.mentor.replica-affinity.sticky:false}") boolean stickyAffinity
    ) {
        this.replicaCount = replicaCount;
        this.stickyAffinity = stickyAffinity;
    }

    @PostConstruct
    void verifyTopology() {
        if (replicaCount > 1 && !stickyAffinity) {
            throw new IllegalStateException(
                "hephaestus.mentor.replica-count=" +
                    replicaCount +
                    " requires hephaestus.mentor.replica-affinity.sticky=true. " +
                    "Mentor sessions are pinned to one JVM (sandbox + lock + SSE emitter are in-process); " +
                    "without sticky routing follow-up turns deadlock. Track #1077 for the infra rollout."
            );
        }
        if (replicaCount > 1) {
            log.info(
                "Mentor running with replicaCount={} and sticky routing — ensure your ingress is configured for (workspaceId, threadId) affinity",
                replicaCount
            );
        }
    }

    int replicaCount() {
        return replicaCount;
    }

    boolean stickyAffinity() {
        return stickyAffinity;
    }
}
