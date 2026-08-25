package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module write command: flip a principal's research-participation consent by SCM login.
 *
 * <p>The counterpart of the read-only {@link AccountPreferencesQuery}. Preferences live in the
 * {@code account} module ({@code user_preferences}); this port is implemented there and consumed by
 * modules that surface a consent control outside the webapp — notably the Slack App Home toggle
 * ({@code integration.slack}) — so neither module imports the other's domain types.
 *
 * <p><strong>Lenient by contract.</strong> Unlike the settings-page path, a consumer of this port
 * cannot supply request context (no HTTP subject, no session). Implementations therefore MUST NOT
 * reject on a missing subject or a downstream analytics failure: an opt-out is a user right and must
 * still take effect. Missing subject / unknown login / analytics-revocation failure are logged, not
 * thrown. See {@code AccountPreferencesService#setForLogin}.
 */
public interface ResearchParticipationCommand {
    /**
     * Set the principal's research-participation flag.
     *
     * @param login       the principal's git-provider login (case-insensitive); a blank or
     *                    unmatched login is a no-op (logged, never thrown)
     * @param participate {@code true} to opt in, {@code false} to opt out; an opt-out (true → false)
     *                    additionally revokes analytics best-effort and writes an audit event
     * @param source      where the decision originated (recorded on the opt-out audit event)
     */
    void setForLogin(String login, boolean participate, ConsentSource source);
}
