package de.tum.cit.aet.hephaestus.core.auth.stepup;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The re-auth gate for high-risk instance-admin actions, anchored on the {@code auth_time} claim (see
 * {@link de.tum.cit.aet.hephaestus.core.auth.jwt.TokenConstraints}). Threat model and why this is
 * deliberately not a second factor: ADR 0017 / {@code docs/contributor/instance-admin.md}.
 *
 * <p>Denials are audited on the gated action's own event type, so blocked attempts sit next to
 * successful ones in the admin audit viewer rather than in a type nobody filters for.
 */
@ConditionalOnServerRole
@Service
public class StepUpPolicy {

    private static final Logger log = LoggerFactory.getLogger(StepUpPolicy.class);

    private final AuthProperties properties;
    private final AuthEventLogger authEventLogger;
    private final AuthMetrics metrics;
    private final Clock clock;

    public StepUpPolicy(AuthProperties properties, AuthEventLogger authEventLogger, AuthMetrics metrics, Clock clock) {
        this.properties = properties;
        this.authEventLogger = authEventLogger;
        this.metrics = metrics;
        this.clock = clock;
        // bootstrap-admin cannot rescue this (it self-disables while an admin exists), so DB surgery
        // would be the only exit — fail closed at startup instead (the DevLoginService idiom).
        if (properties.stepUpMaxAge().isNegative() || properties.stepUpMaxAge().isZero()) {
            throw new IllegalStateException(
                "hephaestus.auth.step-up-max-age must be positive (got " +
                    properties.stepUpMaxAge() +
                    ") — a non-positive window locks every admin out of role changes and impersonation."
            );
        }
        if (properties.stepUpMaxAge().compareTo(properties.sessionMaxLifetime()) >= 0) {
            log.warn(
                "auth.step-up: step-up-max-age ({}) >= session-max-lifetime ({}) — the step-up gate is inert: " +
                    "no session can outlive its ceiling, so auth_time can never be stale.",
                properties.stepUpMaxAge(),
                properties.sessionMaxLifetime()
            );
        }
    }

    /**
     * Enforce the gate: throw {@link StepUpRequiredException} unless {@code authTime} is within
     * {@code step-up-max-age} of now.
     *
     * @param authTime        the actor's {@code auth_time} claim; null (absent) counts as stale.
     * @param auditType       the gated action's event type — the denial is audited on it as FAILURE.
     * @param accountId       the account the action targets (audit {@code account_id}).
     * @param actingAccountId the admin attempting the action (audit {@code acting_account_id}).
     */
    public void requireRecentAuthentication(
        @Nullable Instant authTime,
        AuthEvent.EventType auditType,
        @Nullable Long accountId,
        Long actingAccountId
    ) {
        if (authTime != null && !authTime.isBefore(clock.instant().minus(properties.stepUpMaxAge()))) {
            return;
        }
        log.info(
            "auth.step-up: denied {} for actingAccountId={} (auth_time={}, max-age={})",
            auditType,
            actingAccountId,
            authTime,
            properties.stepUpMaxAge()
        );
        authEventLogger
            .event(auditType, AuthEvent.Result.FAILURE)
            .account(accountId)
            .actingAccount(actingAccountId)
            .failureReason(StepUpRequiredException.CODE)
            .record();
        metrics.recordStepUpDenied(auditType.name().toLowerCase(Locale.ROOT));
        throw new StepUpRequiredException(properties.stepUpMaxAge());
    }
}
