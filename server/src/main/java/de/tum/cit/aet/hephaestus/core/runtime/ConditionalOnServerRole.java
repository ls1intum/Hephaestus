package de.tum.cit.aet.hephaestus.core.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Gates a bean to the SERVER runtime role. Composed from
 * {@code @ConditionalOnProperty(name = hephaestus.runtime.server.enabled, havingValue = "true",
 * matchIfMissing = true)} so the {@code matchIfMissing} invariant (ADR 0005/0008) is declared once,
 * at the source, instead of being repeated — and re-verified — on every gated class.
 *
 * <p>Applied to the user-facing web/auth surface (the whole {@code core.auth} module and
 * {@link de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextFilter}). The worker and
 * webhook pods set {@code hephaestus.runtime.server.enabled=false} and must not load any of it.
 *
 * <p>See {@link RuntimeRole#SERVER_PROPERTY} and {@code RuntimeRoleBoundaryTest}, which enforces that
 * every {@code core.auth} stereotype bean carries this gate.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnServerRole {}
