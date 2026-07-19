package de.tum.cit.aet.hephaestus.core.audit.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an admin-gated mutation endpoint whose action is recorded on the audit trail (its service
 * delegates to {@link ConfigAuditPort}, or to a domain ledger named in {@link #value()}).
 *
 * <p>The counterpart to {@link AuditExempt}: {@code AuditByDefaultArchTest} requires every admin
 * mutation handler to carry exactly one of the two, so a new administrative action cannot reach main
 * without a recorded decision about auditing it. That makes the trail's completeness a property of the
 * build rather than of anyone's memory.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Where the action is recorded — a config-audit entity type, or the name of a domain ledger. */
    String value();
}
