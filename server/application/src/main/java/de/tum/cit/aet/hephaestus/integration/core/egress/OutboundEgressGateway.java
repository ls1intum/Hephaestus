package de.tum.cit.aet.hephaestus.integration.core.egress;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a reviewed adapter that enforces Silent Mode before external delivery writes. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OutboundEgressGateway {}
