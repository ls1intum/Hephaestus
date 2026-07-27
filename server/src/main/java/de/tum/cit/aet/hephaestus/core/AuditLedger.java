package de.tum.cit.aet.hephaestus.core;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The trails an admin action can be recorded on — one constant per append-only table, each carrying
 * the vocabulary that table types its rows with.
 *
 * <p>"Which trail do I go read?" is the first question anyone asks of an {@link Audited} endpoint, so
 * it is the part of the declaration the compiler checks. The vocabulary lives here rather than in the
 * rule that validates it: a ledger and the constants it accepts are one fact, and splitting them is
 * how the two drift.
 */
public enum AuditLedger {
    /** {@code config_audit_event} — per-workspace configuration changes. */
    CONFIG_AUDIT(ConfigAuditEntityType.values()),

    /** {@code auth_event} — authentication, impersonation, and instance-level administration. */
    AUTH_EVENT(AuthEvent.EventType.values()),

    /** {@code connection_audit} — integration connection lifecycle. */
    CONNECTION_AUDIT;

    private final Set<String> vocabulary;

    AuditLedger(Enum<?>... rowTypes) {
        this.vocabulary = Arrays.stream(rowTypes).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The constants this ledger types its rows with. Empty for {@link #CONNECTION_AUDIT}, whose
     * {@code event_type} is a free string composed at write time — there is no constant to name, which
     * is why {@link Audited#type()} is optional.
     */
    public Set<String> vocabulary() {
        return vocabulary;
    }
}
