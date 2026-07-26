package de.tum.cit.aet.hephaestus.agent.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mints and validates proxy-scoped bearer tokens for the mentor's long-lived interactive sandbox
 *. {@code AgentJob} rows carry their own DB-backed job token; the mentor sandbox is
 * NOT an {@code AgentJob} (it is a reused, developer-attached session, not a one-shot NATS-dispatched
 * job), so it needs an equivalent credential minted outside that table.
 *
 * <p>In-memory, process-local — mirrors the fact that the interactive sandbox registry itself
 * (mentor sessions are keyed by {@code (developerId, workspaceId)} and attached per worker process)
 * is already process-local. A token grants exactly what an {@code AgentJob} token grants: the caller
 * can ask the LLM proxy to resolve ONE connection's credential, nothing else.
 *
 * <h2>Revoke-on-teardown</h2>
 *
 * <p>Unlike an {@code AgentJob} token — whose TTL is the job timeout and which is revoked the moment
 * the job transitions terminal — a mentor token has no natural terminal event of its own, so
 * {@link #mint} is also keyed by the sandbox's {@code sessionId}:
 * {@code agent.sandbox.docker.interactive.DockerInteractiveSandboxAdapter}
 * calls {@link #revoke(UUID)} from its dispose path (any close reason — manual, idle-reap, error, or
 * app-server shutdown) the moment the underlying container is gone. {@link #TTL} remains a backstop for
 * the case a sandbox never reaches that callback (e.g. a hard process crash).
 *
 * <h2>Why the TTL is a real expiry, not a check-on-read</h2>
 *
 * <p>The backstop only backstops if it runs on its own. A token whose sandbox never attached (the
 * plan failed, the container never came up) is never presented and never revoked, so a TTL enforced
 * only when that exact token is offered would never fire — and neither entry would ever be dropped.
 * Caffeine expires by wall clock instead, and its eviction listener drops the session index entry in
 * the same step, so a mint that goes nowhere costs bounded memory rather than a leak that lives as
 * long as the worker. {@code maximumSize} is the second bound: a mint storm cannot outgrow it.
 *
 * <h2>Which turn a session's calls belong to</h2>
 *
 * <p>The credential is per SANDBOX SESSION and a session outlives many turns, so on its own it names
 * no billing target — which is what left a mentor turn unmetered for its whole length: it could make
 * an unbounded number of provider calls against an exhausted cap and nothing observed the spend until
 * the turn was over. A turn therefore {@link #bindTurn binds} its {@link MentorTurnMeter} onto the
 * session for the window in which it owns that sandbox, and {@link #unbindTurn unbinds} at the end of
 * that window; {@link #validate} reports the bound turn as the call's billing target.
 *
 * <h2>The bound this guarantees</h2>
 *
 * <blockquote>A mentor turn is refused as soon as its OWN completed calls have consumed the headroom
 * the ledger last showed, so it can overshoot the cap by at most the calls it had already dispatched
 * when the last admitted forward happened — one call for Pi's sequential agent loop — never by the
 * whole turn.</blockquote>
 *
 * <p>Two turns cannot contaminate each other. The binding window is the window in which the turn holds
 * the per-sandbox lock, so at most one turn is bound to a session at any instant, and a call that
 * authenticates after its turn unbound is dropped by the turn-id fence in {@link #accumulate} rather
 * than added to whoever is bound now. The durable half of the same rule is stronger still: the
 * {@code status = 'in_flight'} predicate on {@code ChatMessageRepository#accumulateLlmUsage} means a
 * call carrying turn A's id can only ever reach turn A's row, whatever is bound at the time. Both are
 * the ledger's {@code UNIQUE(source_type, source_id, source_attempt)} rule applied one step earlier,
 * to the mutable accumulators.
 *
 * <p><b>A turn's spend survives the worker that made it.</b> The meter here is only the gate's read
 * model; the record that gets billed is the turn's {@code chat_message} row, written per call by
 * {@code MentorTurnUsageAccumulator}. So a worker that dies mid-turn no longer loses the turn's
 * spend — {@code MentorInFlightReaper} bills the calls the proxy recorded instead of booking a
 * zero-token UNVERIFIABLE event.
 *
 * <p><b>What it does not bound.</b>
 *
 * <ul>
 *   <li>A call made outside a binding window — before the turn's prompt, or during the
 *       client-disconnect drain after it — carries no meter, so it is invisible to the gate. It is
 *       still billed: the row write is fenced on the turn's status, not on the binding.</li>
 *   <li>Turns running concurrently for one workspace are each bounded on their own, so N of them can
 *       together reach N times the cap.</li>
 *   <li>A crashed worker's turn is billed only when the reaper's window elapses (default 70 minutes).
 *       Until then its spend is on the row but not in the ledger, so the gate lets the workspace keep
 *       spending against headroom that is already gone. That is the reaper's latency, not a lost
 *       charge.</li>
 * </ul>
 */
@Component
public class MentorProxyCredentialRegistry {

    private static final Logger log = LoggerFactory.getLogger(MentorProxyCredentialRegistry.class);

    private static final Duration TTL = Duration.ofHours(12);

    /**
     * Far above any plausible count of concurrent mentor sandboxes on one worker — this bounds a
     * pathological mint loop, it is not a working-set limit.
     */
    private static final int MAX_ENTRIES = 10_000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, Entry> byTokenHash;
    private final Map<UUID, String> tokenHashBySession = new ConcurrentHashMap<>();

    /**
     * The meters currently accepting usage, by turn id. Populated by {@link #bindTurn} and removed by
     * {@link #unbindTurn}, so a lookup miss IS the fence: a call whose turn has ended finds nothing
     * and is dropped rather than landing on the turn that is bound now.
     */
    private final Map<UUID, MentorTurnMeter> meterByTurn = new ConcurrentHashMap<>();

    public MentorProxyCredentialRegistry() {
        this(Ticker.systemTicker());
    }

    MentorProxyCredentialRegistry(Ticker ticker) {
        this.byTokenHash = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(TTL)
            .ticker(ticker)
            // Synchronous (unlike removalListener) and fired for expiry and size eviction alike, so the
            // reverse index can never outlive the token it points at. The value-matching remove keeps a
            // re-mint for the same session safe: if this session has since minted a newer token, the
            // index already points at that hash and the stale eviction leaves it alone.
            .<String, Entry>evictionListener((hash, entry, cause) -> {
                if (entry != null) {
                    tokenHashBySession.remove(entry.sessionId(), hash);
                    // A session whose credential expired can no longer serve calls, so its meter can
                    // no longer receive any: drop it here too rather than leave the turn index holding
                    // a meter nothing will ever unbind.
                    MentorTurnMeter bound = entry.currentTurn().getAndSet(null);
                    if (bound != null) {
                        meterByTurn.remove(bound.turnId(), bound);
                    }
                }
            })
            .build();
    }

    /** Non-secret catalog route granted to one mentor sandbox. */
    public record Route(
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId
    ) {}

    /**
     * Routing for a minted mentor proxy token, plus the session it belongs to and the turn — if any —
     * currently spending on it.
     *
     * @param currentTurn the meter calls on this token are billed to right now; {@code null} between
     *     turns. Mutable because the routing is fixed for the session's whole life while the turn
     *     underneath it changes many times.
     */
    private record Entry(
        UUID sessionId,
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId,
        AtomicReference<@Nullable MentorTurnMeter> currentTurn
    ) {}

    /**
     * Mint a fresh token for a mentor sandbox build. Never returns the same token twice.
     *
     * @param sessionId the sandbox's {@code InteractiveSandboxSpec#sessionId} — the correlation key
     *     {@link #revoke(UUID)} uses to find this token again at sandbox teardown
     */
    public String mint(UUID sessionId, Route route) {
        byte[] bytes = new byte[32]; // 256 bits — same shape as AgentJob's job token
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = AgentJob.computeTokenHash(token);
        byTokenHash.put(
            hash,
            new Entry(
                sessionId,
                route.apiProtocol(),
                route.baseUrl(),
                route.connectionScope(),
                route.connectionId(),
                route.modelId(),
                route.workspaceId(),
                new AtomicReference<>()
            )
        );
        // A re-mint for the same session orphans the previous token; drop it now rather than leaving
        // two live credentials for one sandbox until the older one's TTL runs out.
        String previous = tokenHashBySession.put(sessionId, hash);
        if (previous != null && !previous.equals(hash)) {
            byTokenHash.invalidate(previous);
        }
        return token;
    }

    /**
     * Validate a bearer token. Empty when unknown or expired.
     *
     * <p>The routing names the turn bound to this session at the instant the call authenticates, and
     * carries what that turn has already spent — which is what makes the turn's own in-flight spend
     * visible to {@code ProxyBudgetGate}. A call that arrives between turns carries no billing target;
     * it is still judged against the workspace's recorded spend, just without an in-flight term.
     */
    public Optional<ProxyRouting> validate(String token) {
        Entry entry = byTokenHash.getIfPresent(AgentJob.computeTokenHash(token));
        if (entry == null) {
            return Optional.empty();
        }
        MentorTurnMeter turn = entry.currentTurn().get();
        return Optional.of(
            new ProxyRouting(
                "mentor-session",
                entry.apiProtocol(),
                entry.baseUrl(),
                entry.connectionScope(),
                entry.connectionId(),
                entry.modelId(),
                entry.workspaceId(),
                turn == null
                    ? null
                    : new ProxyRouting.BilledAttempt(
                          LlmUsageSourceType.MENTOR_TURN,
                          turn.turnId(),
                          // A turn never retries, so there is only ever attempt 0 of a given turn id.
                          0,
                          turn.spentUsd()
                      )
            )
        );
    }

    /**
     * Start billing this session's calls to {@code meter}, for as long as the turn owns the sandbox.
     *
     * <p>Replaces any previously bound turn rather than refusing: the caller is the turn that holds
     * the per-sandbox lock, so a leftover binding means an earlier turn failed to unbind, and the
     * live turn is the correct target. The displaced meter is removed from the turn index in the same
     * step so it stops accepting usage — it never silently keeps collecting under a turn that ended.
     *
     * @return false when the session has no live credential (revoked, or expired past {@link #TTL}),
     *     in which case the turn runs unmetered; the caller is expected to make that observable.
     */
    public boolean bindTurn(UUID sessionId, MentorTurnMeter meter) {
        String hash = tokenHashBySession.get(sessionId);
        Entry entry = hash == null ? null : byTokenHash.getIfPresent(hash);
        if (entry == null) {
            return false;
        }
        meterByTurn.put(meter.turnId(), meter);
        MentorTurnMeter displaced = entry.currentTurn().getAndSet(meter);
        if (displaced != null && displaced != meter) {
            meterByTurn.remove(displaced.turnId(), displaced);
            log.warn(
                "Mentor turn {} was still bound to sandbox session {} when turn {} started — " +
                    "its later calls will go unbilled rather than being charged to the new turn",
                displaced.turnId(),
                sessionId,
                meter.turnId()
            );
        }
        return true;
    }

    /**
     * Stop billing new calls to {@code meter}. Idempotent, and value-matching on both indexes so a
     * late unbind from a turn that has already been displaced cannot detach the turn running now.
     *
     * <p>Does NOT clear what the meter observed: the terminal write still reads the whole turn from
     * the reference it holds.
     */
    public void unbindTurn(UUID sessionId, MentorTurnMeter meter) {
        meterByTurn.remove(meter.turnId(), meter);
        String hash = tokenHashBySession.get(sessionId);
        Entry entry = hash == null ? null : byTokenHash.getIfPresent(hash);
        if (entry != null) {
            entry.currentTurn().compareAndSet(meter, null);
        }
    }

    /**
     * Mirror one served call's tokens onto the turn that authenticated it, so the gate sees them on
     * the next call. Call this only for a call already recorded on the turn's row — the row is the
     * billing record and this is derived from it, never the other way round.
     *
     * @return false when that turn is no longer bound. Nothing is billed or lost by that: the money is
     *     already on the row, and a turn that has stopped owning its sandbox will not be gated again.
     *     It matters only that the tokens are not silently added to whatever turn IS bound — that
     *     would make the gate refuse a different turn for spend it never incurred.
     */
    public boolean accumulate(UUID turnId, ProxyTokenUsage usage) {
        MentorTurnMeter meter = meterByTurn.get(turnId);
        if (meter == null) {
            return false;
        }
        meter.add(usage);
        return true;
    }

    /**
     * Revoke the token minted for a sandbox session, if any. Idempotent — a second call (or a call for
     * a session that never minted a token, e.g. it lost the concurrent-attach race) is a harmless no-op.
     */
    public void revoke(UUID sessionId) {
        String hash = tokenHashBySession.remove(sessionId);
        if (hash != null) {
            Entry entry = byTokenHash.getIfPresent(hash);
            if (entry != null) {
                MentorTurnMeter bound = entry.currentTurn().getAndSet(null);
                if (bound != null) {
                    meterByTurn.remove(bound.turnId(), bound);
                }
            }
            byTokenHash.invalidate(hash);
        }
    }

    /** Test seam: run pending expiry work so a {@link Ticker} advance takes effect deterministically. */
    void runPendingEviction() {
        byTokenHash.cleanUp();
    }

    /** Test seam: how many session-index entries are currently held. */
    int trackedSessions() {
        return tokenHashBySession.size();
    }

    /** Test seam: how many turns are currently accepting usage. */
    int boundTurns() {
        return meterByTurn.size();
    }
}
