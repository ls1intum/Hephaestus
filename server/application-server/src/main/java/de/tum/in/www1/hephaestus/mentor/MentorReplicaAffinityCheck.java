package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Operator-asserted startup guard for multi-replica mentor deployments.
 *
 * <p>Live mentor sessions are pinned to one app-server replica because the
 * {@code InteractiveSandboxRegistry} is in-process: the {@code AttachedSandbox} owning a
 * thread's stdin/stdout, the per-turn {@code MentorTurnLock}, and the in-flight {@code SseEmitter}
 * all live in JVM memory. Routing a follow-up turn to a different replica deadlocks (the
 * sandbox is on a peer) and the {@code ux_chat_message_in_flight} index is the only durable
 * backstop against poisoned threads.
 *
 * <p>The proper fix is sticky sessions per {@code (workspaceId, threadId)} — tracked in #1077.
 *
 * <h3>Honesty note (Loop-2 audit)</h3>
 * This check reads {@code hephaestus.mentor.replica-count} which is OPERATOR-ASSERTED, not
 * derived from the orchestrator. An operator who scales via {@code kubectl scale --replicas=3}
 * <em>without</em> updating the property silently bypasses this guard. The class name and
 * Javadoc imply infra-level enforcement; in reality it catches the misconfiguration where
 * someone bumps the property without also flipping the sticky-affinity flag. Until the
 * topology can be read from a real source (Kubernetes downward API / service discovery),
 * treat this as a config-consistency tripwire, not a deployment safety net.
 */
@Component
@WorkspaceAgnostic("Cluster-topology guard — not tenant-scoped")
public class MentorReplicaAffinityCheck {

    private static final Logger log = LoggerFactory.getLogger(MentorReplicaAffinityCheck.class);

    private final int replicaCount;
    private final boolean stickyAffinity;

    /**
     * {@code @Value} bindings rather than constructor-injection of {@code InteractiveSandboxProperties}:
     * pulling the record into this module created a {@code mentor → agent.sandbox} edge that
     * ArchUnit's modularity cycle rule rejects. {@code @Min(1)} validation on the sibling
     * record still fires at Spring property-bind time (the record is consumed by
     * {@code DockerSandboxConfiguration}), so a misconfigured {@code replica-count=0} fails at
     * boot regardless of whether this check is read.
     */
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
