package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Architecture rules pinning the placement of {@link Transactional @Transactional} on the
 * mentor aspect providers ({@code UserAspectProvider}, {@code WorkspaceAspectProvider},
 * {@code PracticeCatalogAspectProvider}, {@code FindingsHistoryAspectProvider}).
 *
 * <p><b>The bug shape this guards against:</b> Spring AOP intercepts {@code @Transactional} via
 * the bean proxy. A class's own {@code this.method(...)} call bypasses the proxy entirely, so an
 * annotation on a helper that is only ever invoked through a self-call is silently dropped
 * (Spring docs: <i>"Calling a method on the proxy from within the target object will not be
 * intercepted"</i>). Combined with {@code spring.jpa.open-in-view=false}, the annotation looks
 * load-bearing in the code but actually does nothing — exactly the failure mode the audit caught
 * in loop-3.
 *
 * <p>The fix was to move {@code @Transactional(readOnly = true)} from each provider's helper
 * {@code buildPayload(...)} to {@code contribute(...)}, which IS the external entry point
 * {@code WorkspaceContextBuilder} calls through the proxy. These rules pin that fix so a future
 * refactor that moves the annotation back onto the helper fails at architecture-test time
 * instead of at runtime via {@code LazyInitializationException} on the SSE wire.
 */
@DisplayName("Mentor aspect provider transactional placement")
class MentorAspectProviderArchitectureTest extends HephaestusArchitectureTest {

    private static final String MENTOR_PROVIDERS_PACKAGE = "..agent.context.providers.mentor..";

    @Test
    @DisplayName("contribute(ContextRequest, Map) on every *AspectProvider must be @Transactional")
    void contributeIsTransactional() {
        ArchRule rule = methods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage(MENTOR_PROVIDERS_PACKAGE)
            .and()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("AspectProvider")
            .and()
            .haveName("contribute")
            .should()
            .beAnnotatedWith(Transactional.class)
            .because(
                "contribute() is the external entry point WorkspaceContextBuilder calls through the " +
                    "Spring proxy. Removing @Transactional from here OR moving it to a helper would " +
                    "silently drop the tx (self-invocation bypasses the proxy) and re-introduce the " +
                    "LazyInitializationException risk loop-3 fixed. See javadoc on each contribute()."
            );
        rule.check(classes);
    }

    @Test
    @DisplayName("buildPayload(...) on every *AspectProvider must NOT be @Transactional (self-invocation no-op)")
    void buildPayloadIsNotTransactional() {
        ArchRule rule = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage(MENTOR_PROVIDERS_PACKAGE)
            .and()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("AspectProvider")
            .and()
            .haveName("buildPayload")
            .should()
            .beAnnotatedWith(Transactional.class)
            .because(
                "buildPayload() is only ever invoked via this.buildPayload(...) from contribute(). " +
                    "Spring AOP cannot intercept self-invocations, so @Transactional here is silently " +
                    "dropped — the comment lies about the safety net. Annotation belongs on contribute()."
            );
        rule.check(classes);
    }
}
