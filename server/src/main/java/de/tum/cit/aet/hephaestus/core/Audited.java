package de.tum.cit.aet.hephaestus.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an admin-gated mutation endpoint whose action is recorded on the audit trail.
 *
 * <p>The counterpart to {@link AuditExempt}: {@code AuditByDefaultArchTest} requires every admin
 * mutation handler to carry exactly one of the two, so a new administrative action cannot reach main
 * without a recorded decision about auditing it. That makes the trail's completeness a property of the
 * build rather than of anyone's memory.
 *
 * <h2>Grammar of {@link #value()}: {@code "<ledger>[ <TOKEN>]"}</h2>
 *
 * <p><b>The value always begins with the name of the table the row lands in</b> — {@code config_audit},
 * {@code auth_event} or {@code connection_audit} — because "which trail do I go read?" is the first
 * question anyone asks of this annotation, and a bare {@code WORKSPACE_ROLE} does not answer it. An
 * optional second token names the record within that ledger, using the ledger's own vocabulary:
 *
 * <ul>
 *   <li>{@code @Audited("config_audit WORKSPACE_ROLE")} — the token is a
 *       {@link de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType} constant.</li>
 *   <li>{@code @Audited("auth_event LLM_MODEL_CREATED")} — the token is an
 *       {@code AuthEvent.EventType} constant.</li>
 *   <li>{@code @Audited("connection_audit")} — no token: that ledger's {@code event_type} is a free
 *       string composed at write time, so there is no constant to name.</li>
 * </ul>
 *
 * <p>{@code AuditByDefaultArchTest} parses the value: an unknown ledger, or a token that is not a real
 * constant of its ledger's enum, fails the build. Before that check existed a typo'd entity type
 * silently opted the endpoint out of the "does it actually reach a recorder?" rule.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Where the action is recorded: {@code "<ledger>[ <TOKEN>]"} — see the type javadoc. */
    String value();
}
