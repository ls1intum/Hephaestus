package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The routing shape a validated proxy credential (an {@code AgentJob} token or a mentor session
 * token) resolves to. Carries the FROZEN, non-secret behaviour (api protocol + upstream base URL —
 * see {@code ConfigSnapshot}) plus enough of a connection reference for
 * {@code LlmModelResolver#resolveProxyCredential} to re-resolve the LIVE credential + header
 * material. Never carries the credential itself.
 *
 * @param principalDescription log/metrics-safe identifier of the caller (job id or mentor session
 *     description) — never the token.
 * @param attempt which execution this call is billed to and what it has spent so far; {@code null}
 *     only for a mentor session between turns (a job token always names its attempt), and
 *     {@code LlmProxyController} refuses those calls rather than serving spend nothing can record.
 */
public record ProxyRouting(
    String principalDescription,
    String apiProtocol,
    String baseUrl,
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    @Nullable Long modelId,
    @Nullable Long workspaceId,
    @Nullable BilledAttempt attempt
) {
    /**
     * The one execution — one attempt of one {@code agent_job}, or one mentor turn — that this
     * credential bills to, resolved when the token was authenticated.
     *
     * <p>The identity fields are deliberately the ledger's own:
     * {@code UNIQUE(source_type, source_id, source_attempt)}. Naming an in-flight billing target the
     * same way the finished ledger row names it is what lets the two be checked against each other,
     * and what keeps "which execution is this" from meaning something different before and after the
     * run ends.
     *
     * <p>The four fields belong together because they are all answers about the SAME execution, read
     * in the same instant: which run made this call, and what that run has already consumed.
     * Splitting them would let a caller pair one execution's identity with another's spend.
     *
     * <p><b>Why an attempt number and not just an id.</b> Every write keyed on the id alone is racy
     * for an agent job: orphan recovery can requeue the row (bumping {@code retry_count} and zeroing
     * the per-attempt token accumulators) while a call this credential authenticated is still waiting
     * on the provider. The number lets a late write be recognised as belonging to a superseded attempt
     * and dropped, instead of being added to — and billed against — the attempt that now owns the row.
     * A mentor turn never retries, so its number is always {@code 0}; its fence is the turn id itself,
     * which is a fresh {@code chat_message} id per turn and is never reused.
     *
     * @param sourceType which kind of execution {@link #sourceId} names, and therefore which
     *     accumulator a served call's tokens are added to
     * @param sourceId the {@code agent_job} id, or the assistant {@code chat_message} id of the mentor
     *     turn, that this route bills to
     * @param number the row's {@code retry_count} when the token was validated ({@code 0} for a mentor
     *     turn); the same value the ledger stores as {@code source_attempt}, so the two agree on what
     *     "an attempt" is
     * @param spentUsd what this execution's already-completed proxied calls cost, priced with the rates
     *     frozen onto it at admission. {@link BigDecimal#ZERO} when it has made no billable call yet,
     *     or when the frozen snapshot carries no price at all — unreachable for work admitted through
     *     {@code LlmAdmissionService}, which refuses to start an unpriced model.
     */
    public record BilledAttempt(LlmUsageSourceType sourceType, UUID sourceId, int number, BigDecimal spentUsd) {}

    /** The {@code agent_job} or mentor-turn id this call bills to, or {@code null} when nothing does. */
    public @Nullable UUID sourceId() {
        return attempt == null ? null : attempt.sourceId();
    }

    /**
     * Spend this workspace has already incurred that the ledger cannot see yet — zero unless a live
     * execution is behind this call, since the ledger is only appended when a run ends.
     */
    public BigDecimal inFlightSpendUsd() {
        return attempt == null ? BigDecimal.ZERO : attempt.spentUsd();
    }
}
