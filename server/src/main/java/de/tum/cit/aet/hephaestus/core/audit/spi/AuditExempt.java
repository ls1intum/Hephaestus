package de.tum.cit.aet.hephaestus.core.audit.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an admin-gated mutation endpoint that is deliberately NOT on the audit trail, with a mandatory
 * reason. The counterpart to {@link Audited}: it turns every known gap into a greppable, reviewed
 * decision instead of a silent hole.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditExempt {
    /** Why this admin mutation is not audited (e.g. "read-model rebuild, changes no configuration"). */
    String reason();
}
