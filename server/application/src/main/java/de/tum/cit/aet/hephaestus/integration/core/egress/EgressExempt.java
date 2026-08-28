package de.tum.cit.aet.hephaestus.integration.core.egress;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares, with a reviewable reason, a control-plane writer exempt from Silent Mode. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EgressExempt {
    EgressExemption value();
}
