package de.tum.cit.aet.hephaestus.core.audit;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditActorKind;
import de.tum.cit.aet.hephaestus.core.auth.web.CurrentAccount;
import org.jspecify.annotations.Nullable;

/**
 * Who caused a configuration change, resolved from the security context rather than passed in by the
 * producer — so a producer can neither forget it nor spoof it.
 */
record ConfigAuditActor(ConfigAuditActorKind kind, @Nullable Long accountId, @Nullable Long actingAccountId) {
    /**
     * The kind is decided by whether anyone is <em>authenticated</em>, not by whether their id parses.
     * Those are different questions, and conflating them is how an audit trail starts lying: derive the
     * kind from the id and a signed-in human whose token subject is unreadable gets filed as
     * {@link ConfigAuditActorKind#SYSTEM} — "a background job did this" — which is exactly the
     * confusion {@code actor_kind} exists to prevent.
     *
     * <p>So: no authentication at all means SYSTEM (a seeder, scheduler, or platform-event handler).
     * An authenticated principal is a USER (or IMPERSONATED) even if the subject cannot be resolved to
     * an account id, in which case the id is left null — honest about who acted and honest about what
     * we failed to resolve. Production subjects are always numeric ({@code HephaestusJwtIssuer} writes
     * {@code String.valueOf(accountId)}), so the unresolved case is not reachable there.
     */
    static ConfigAuditActor fromSecurityContext() {
        if (!CurrentAccount.isAuthenticated()) {
            return new ConfigAuditActor(ConfigAuditActorKind.SYSTEM, null, null);
        }
        Long accountId = CurrentAccount.idOrNull();
        Long impersonator = CurrentAccount.impersonatorId();
        if (impersonator != null) {
            return new ConfigAuditActor(ConfigAuditActorKind.IMPERSONATED, accountId, impersonator);
        }
        return new ConfigAuditActor(ConfigAuditActorKind.USER, accountId, null);
    }
}
