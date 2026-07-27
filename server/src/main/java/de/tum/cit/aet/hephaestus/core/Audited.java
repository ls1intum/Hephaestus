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
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Which trail the action lands on. */
    AuditLedger ledger();

    /**
     * Which row type within {@link #ledger()}'s {@link AuditLedger#vocabulary() vocabulary}. Left
     * empty only for a ledger that has none; {@code AuditByDefaultArchTest} fails the build on any
     * other combination, since a token that names no constant would silently opt the endpoint out of
     * the "does it actually reach a recorder?" rule.
     */
    String type() default "";
}
