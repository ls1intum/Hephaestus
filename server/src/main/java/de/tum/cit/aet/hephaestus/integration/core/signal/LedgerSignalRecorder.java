package de.tum.cit.aet.hephaestus.integration.core.signal;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code artifact_signal} table as a {@link SignalRecorder}.
 *
 * <p>Every method demands the caller's transaction. A signal that was recorded but whose consequence
 * rolled back would be dedup for a review that never happened, so the record and the decision it
 * licenses must succeed or fail together.
 */
@Service
public class LedgerSignalRecorder implements SignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(LedgerSignalRecorder.class);

    private final ArtifactSignalRepository repository;

    public LedgerSignalRecorder(ArtifactSignalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(SignalKey key, Instant occurredAt, DiscoveredVia discoveredVia) {
        Instant now = Instant.now();
        // A reconciliation pass knows that something happened, not that it is the right one to act on
        // it, so it may only ever add a row; a live or requested observation may additionally take over
        // one nobody has decided yet.
        //
        // A BACKFILL deliberately takes the second branch, and that is the whole mechanism: a first sync
        // leaves thousands of rows RECORDED-but-undecided precisely so it does not fire thousands of
        // reviews, and a confirmed campaign is the thing that is finally entitled to claim them.
        int affected =
            discoveredVia == DiscoveredVia.SYNC
                ? repository.insertIfAbsent(key, UUID.randomUUID(), occurredAt, discoveredVia.name(), now)
                : repository.insertOrClaimUndecided(key, UUID.randomUUID(), occurredAt, discoveredVia.name(), now);
        if (affected == 0) {
            log.debug(
                "Signal already settled, not acting: workspaceId={}, signal={}, artifactId={}, revision={}",
                key.workspaceId(),
                key.signalName(),
                key.artifactId(),
                key.revision()
            );
        }
        return affected == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markTriggered(SignalKey key, UUID jobId) {
        int affected = repository.markTriggered(key, jobId, Instant.now());
        if (affected == 0) {
            logUnsettled("triggered", key);
            return;
        }
        log.debug(
            "Signal triggered: workspaceId={}, signal={}, artifactId={}, jobId={}",
            key.workspaceId(),
            key.signalName(),
            key.artifactId(),
            jobId
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markRefused(SignalKey key, SignalStateReason reason) {
        int affected = repository.markRefused(key, reason.resultingState().name(), reason.name(), Instant.now());
        if (affected == 0) {
            logUnsettled("refused", key);
            return;
        }
        log.debug(
            "Signal refused: workspaceId={}, signal={}, artifactId={}, reason={}, state={}",
            key.workspaceId(),
            key.signalName(),
            key.artifactId(),
            reason,
            reason.resultingState()
        );
    }

    /**
     * A settle that matched nothing. Either the row is gone, or somebody already decided it — both mean
     * this caller's decision is void, and both are worth saying out loud rather than discarding: the
     * ledger's whole purpose is that no signal ends up with no explanation.
     */
    private void logUnsettled(String attempted, SignalKey key) {
        log.warn(
            "Signal was not settleable as {}, leaving whoever decided it: workspaceId={}, signal={}," +
                " artifactId={}, revision={}",
            attempted,
            key.workspaceId(),
            key.signalName(),
            key.artifactId(),
            key.revision()
        );
    }
}
