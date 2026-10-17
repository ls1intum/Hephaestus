package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/** Governed capture runs where reviews are prepared, on the server or a worker. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnExpression("${" + RuntimeRole.SERVER_PROPERTY + ":true} or ${" + RuntimeRole.WORKER_PROPERTY + ":true}")
public @interface ConditionalOnCaptureRole {}
