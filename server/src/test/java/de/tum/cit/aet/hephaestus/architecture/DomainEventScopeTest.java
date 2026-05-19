package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Enforces the sealed scope of {@code DomainEvent} subclasses to the ingest pipeline
 * (gitprovider.{github|gitlab} processors).
 *
 * <p>Why: per ADR 0005, the foundations epic commits to "the ingest side of sync owns
 * DomainEvent publication". Listeners on the consumer side (achievement, activity,
 * agent dispatch) react to these events in-process. The moment a non-ingest module
 * starts publishing DomainEvent subclasses, the cross-runtime contract (in-process
 * events between sync handlers and consumers) becomes ambiguous — and worse, the
 * MentorContextInvalidator-class of cross-process event bugs that the principal-
 * engineer pressure test surfaced becomes possible.
 *
 * <p>Scope rule: only classes under {@code gitprovider..github..} or
 * {@code gitprovider..gitlab..} may instantiate or invoke methods on subclasses of
 * {@code DomainEvent}. Today's codebase satisfies this (verified during planning).
 * Test runs in the {@code architecture} surefire group on every PR.
 */
@Tag("architecture")
@DisplayName("Domain Event Publication Scope")
class DomainEventScopeTest extends HephaestusArchitectureTest {

    private static final String DOMAIN_EVENT_FQN =
        "de.tum.cit.aet.hephaestus.gitprovider.common.events.DomainEvent";

    @Test
    @DisplayName("only gitprovider.{github,gitlab}.** may publish DomainEvent")
    void onlyIngestProcessorsPublishDomainEvents() {
        ArchRule rule = classes()
            .that()
            .areAssignableTo(DOMAIN_EVENT_FQN)
            .and()
            .doNotHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)
            .should()
            .resideInAnyPackage(
                "..gitprovider..github..",
                "..gitprovider..gitlab..",
                "..gitprovider..common.events.."
            )
            .because(
                "DomainEvent subclasses are scoped to the ingest pipeline. "
                + "Cross-module reactions belong in @TransactionalEventListener handlers "
                + "or NATS for cross-process invariants; see ADR 0005."
            );

        // Filter out events imported only — focus on classes that EXTEND DomainEvent.
        rule.check(classes.that(extendsDomainEvent()));
    }

    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> extendsDomainEvent() {
        return new com.tngtech.archunit.base.DescribedPredicate<>("subclass of DomainEvent") {
            @Override
            public boolean test(JavaClass clazz) {
                return clazz
                    .getRawSuperclass()
                    .map(c -> c.getFullName().startsWith(DOMAIN_EVENT_FQN))
                    .orElse(false);
            }
        };
    }
}
