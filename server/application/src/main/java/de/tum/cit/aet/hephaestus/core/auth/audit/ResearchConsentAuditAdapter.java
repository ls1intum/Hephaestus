package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.auth.spi.ConsentSource;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchConsentAudit;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * In-{@code core.auth} implementation of {@link ResearchConsentAudit}. Keeps the
 * {@code RESEARCH_CONSENT_REVOKED} event type + the audit-write machinery encapsulated inside
 * {@code core.auth} (dependency inversion: the {@code account} module consumes only the SPI port,
 * never {@link AuthEvent} / {@link AuthEventLogger}).
 *
 * <p>Best-effort — {@link AuthEventLogger.Draft#record()} already swallows its own failures so a
 * research opt-out never breaks on the audit write.
 */
@ConditionalOnServerRole
@Component
public class ResearchConsentAuditAdapter implements ResearchConsentAudit {

    private final AuthEventLogger authEventLogger;

    public ResearchConsentAuditAdapter(AuthEventLogger authEventLogger) {
        this.authEventLogger = authEventLogger;
    }

    @Override
    public void recordOptOut(String login, ConsentSource source) {
        authEventLogger
            .event(AuthEvent.EventType.RESEARCH_CONSENT_REVOKED, AuthEvent.Result.SUCCESS)
            .details(
                "{\"source\":\"" +
                    jsonEscape(source == null ? null : source.name()) +
                    "\",\"login\":\"" +
                    jsonEscape(login) +
                    "\"}"
            )
            .record();
    }

    /** Minimal JSON string escaping for the free-text values embedded in the audit {@code details} object. */
    private static String jsonEscape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
