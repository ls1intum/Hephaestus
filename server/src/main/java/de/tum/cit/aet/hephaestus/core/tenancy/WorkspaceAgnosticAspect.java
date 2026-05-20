package de.tum.cit.aet.hephaestus.core.tenancy;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes {@link WorkspaceAgnostic} load-bearing at runtime: while an annotated method (or a
 * method on an annotated class) is on the stack, {@link TenancyBypass} reports active and
 * {@link WorkspaceStatementInspector} treats emitted SQL as exempt.
 *
 * <p>Spring AOP proxy model limitations the aspect inherits:
 * <ul>
 *   <li>Static methods are not advised.</li>
 *   <li>Self-invocation ({@code this.foo()} inside the same bean) bypasses the proxy.</li>
 *   <li>{@code @Async} hops onto a worker thread; the bypass scope does not propagate
 *       unless the worker explicitly re-opens it.</li>
 * </ul>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceAgnosticAspect {

    @Around("@annotation(de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic)")
    public Object aroundAnnotatedMethod(ProceedingJoinPoint pjp) throws Throwable {
        WorkspaceAgnostic annotation = ((MethodSignature) pjp.getSignature())
            .getMethod()
            .getAnnotation(WorkspaceAgnostic.class);
        return proceedWithBypass(pjp, annotation);
    }

    @Around("@within(de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic) "
        + "&& !@annotation(de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic)")
    public Object aroundAnnotatedType(ProceedingJoinPoint pjp) throws Throwable {
        Class<?> declaringType = pjp.getSignature().getDeclaringType();
        WorkspaceAgnostic annotation = declaringType.getAnnotation(WorkspaceAgnostic.class);
        return proceedWithBypass(pjp, annotation);
    }

    private static Object proceedWithBypass(ProceedingJoinPoint pjp, WorkspaceAgnostic ann)
        throws Throwable {
        String reason = ann != null ? ann.value() : "anonymous";
        try (TenancyBypass.Scope ignored = TenancyBypass.open(reason)) {
            return pjp.proceed();
        }
    }
}
