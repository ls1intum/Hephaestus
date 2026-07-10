package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module audit port: record a research-participation opt-out on the append-only auth-event trail.
 *
 * <p>The {@code account} module owns the preference row but must not reach into {@code core.auth}'s
 * internal {@code audit} package to write an {@link ConsentSource}-attributed audit event. This port
 * inverts that: {@code core.auth} implements it (over its {@code AuthEventLogger} + the
 * {@code RESEARCH_CONSENT_REVOKED} event type) and the preference owner consumes it. Best-effort by
 * contract — an audit write must never break the consent flow, so implementations swallow their own
 * failures (mirroring the {@code AuthEventWriter} guarantee).
 */
public interface ResearchConsentAudit {
    /**
     * Append a {@code RESEARCH_CONSENT_REVOKED} event for the given principal.
     *
     * @param login  the principal's git-provider login (recorded on the event)
     * @param source where the opt-out originated
     */
    void recordOptOut(String login, ConsentSource source);
}
