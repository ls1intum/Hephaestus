package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Provenance of a research-participation consent decision — the surface the user acted on when they
 * opted in or out. Recorded on the {@code RESEARCH_CONSENT_REVOKED} audit event's {@code details} so
 * an opt-out is attributable to the exact channel it came from (settings page vs. Slack App Home),
 * which the legitimate-interest / GDPR trail requires.
 *
 * <p>Part of the {@link ResearchParticipationCommand} SPI contract: a consumer must state where the
 * decision originated so no opt-out is recorded without provenance.
 */
public enum ConsentSource {
    /** The webapp account-settings page ({@code PATCH /user/settings}). */
    SETTINGS_UI,
    /** The Slack App Home research-participation toggle. */
    SLACK_APP_HOME,
}
