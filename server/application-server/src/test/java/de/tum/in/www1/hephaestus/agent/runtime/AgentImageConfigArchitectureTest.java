package de.tum.in.www1.hephaestus.agent.runtime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.tum.in.www1.hephaestus.architecture.HephaestusArchitectureTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Drift guard: only {@link AgentImageProperties} may hard-code a container registry reference.
 *
 * <p>Issue #1076 collapsed two parallel image properties onto one record. Spring's
 * {@code @DefaultValue} is {@code @Target(PARAMETER)} only, so it lands on canonical-constructor
 * parameters of {@code @ConfigurationProperties} records — not on synthetic backing fields. This
 * rule scans those parameters and rejects any default whose value contains {@code ghcr.io/} or
 * {@code @sha256:} outside {@link AgentImageProperties}.
 */
@DisplayName("Agent image config")
class AgentImageConfigArchitectureTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("only AgentImageProperties may hard-code a container registry reference")
    void shouldCentraliseImageConfigWhenScanningConfigurationProperties() {
        constructors()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(ConfigurationProperties.class)
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName(AgentImageProperties.class.getName())
            .should(new RegistryReferenceCondition())
            .because("issue #1076 — image config has exactly one home (AgentImageProperties)")
            .check(classes);
    }

    private static final class RegistryReferenceCondition extends ArchCondition<JavaConstructor> {

        RegistryReferenceCondition() {
            super("not declare a @DefaultValue containing 'ghcr.io/' or '@sha256:'");
        }

        @Override
        public void check(JavaConstructor ctor, ConditionEvents events) {
            for (JavaParameter parameter : ctor.getParameters()) {
                parameter
                    .tryGetAnnotationOfType(DefaultValue.class)
                    .ifPresent(dv -> {
                        for (String value : dv.value()) {
                            if (value.contains("ghcr.io/") || value.contains("@sha256:")) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        ctor,
                                        ctor.getFullName() +
                                            " parameter " +
                                            parameter.getIndex() +
                                            " has registry-ish @DefaultValue: " +
                                            value
                                    )
                                );
                            }
                        }
                    });
            }
        }
    }
}
