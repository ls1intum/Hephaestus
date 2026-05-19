package de.tum.cit.aet.hephaestus.core.tenancy;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * AOP advice that makes {@link WorkspaceAgnostic} load-bearing at runtime: when a method or
 * a method on an annotated class is invoked, this aspect opens a bypass scope on
 * {@code WorkspaceContextHolder} for the duration of the call.
 *
 * <p>{@link WorkspaceStatementInspector} reads {@code WorkspaceContextHolder.isBypassActive()}
 * before parsing SQL. With bypass active, statements are passed through unmodified —
 * legitimate cross-workspace queries (workspace listing, slug history, admin maintenance)
 * don't need a hardcoded allowlist entry.
 *
 * <p>Two pointcuts: {@code @annotation} (method-level) and {@code @within} (class-level).
 * If both apply (annotated method on annotated class), the depth counter handles nesting.
 */
@Aspect
@Component
public class WorkspaceAgnosticAspect {

    @Around("@annotation(de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic)")
    public Object aroundAnnotatedMethod(ProceedingJoinPoint pjp) throws Throwable {
        WorkspaceAgnostic annotation = ((MethodSignature) pjp.getSignature())
            .getMethod()
            .getAnnotation(WorkspaceAgnostic.class);
        return proceedWithOptionalBypass(pjp, annotation);
    }

    @Around("@within(de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic) "
        + "&& !@annotation(de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic)")
    public Object aroundAnnotatedType(ProceedingJoinPoint pjp) throws Throwable {
        Class<?> declaringType = pjp.getSignature().getDeclaringType();
        WorkspaceAgnostic annotation = declaringType.getAnnotation(WorkspaceAgnostic.class);
        return proceedWithOptionalBypass(pjp, annotation);
    }

    private static Object proceedWithOptionalBypass(ProceedingJoinPoint pjp, WorkspaceAgnostic ann)
        throws Throwable {
        if (ann == null || !ann.runtimeBypass()) {
            return pjp.proceed();
        }
        try (AutoCloseable ignored = WorkspaceContextHolder.openBypass(ann.value())) {
            return pjp.proceed();
        }
    }
}
