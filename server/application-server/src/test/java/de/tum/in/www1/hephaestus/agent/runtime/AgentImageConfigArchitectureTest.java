package de.tum.in.www1.hephaestus.agent.runtime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.core.domain.JavaField;
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
 * <p>Issue #1076 collapsed two parallel image properties (Pi practice + mentor) onto one record.
 * Attacks the underlying invariant — "no {@code @DefaultValue} containing {@code ghcr.io/} or
 * {@code @sha256:} outside {@code AgentImageProperties}" — rather than a hard-coded field-name
 * list, so the rule catches drift regardless of whether someone names their new field
 * {@code dockerImage}, {@code runtime}, or anything else.
 */
@DisplayName("Agent image config")
class AgentImageConfigArchitectureTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("only AgentImageProperties may hard-code a container registry reference")
    void imageConfigIsCentralised() {
        fields()
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

    /** Flags any {@code @DefaultValue("...ghcr.io/...")} or {@code "...@sha256:..."}. */
    private static final class RegistryReferenceCondition extends ArchCondition<JavaField> {

        RegistryReferenceCondition() {
            super("not carry a @DefaultValue containing 'ghcr.io/' or '@sha256:'");
        }

        @Override
        public void check(JavaField field, ConditionEvents events) {
            field
                .tryGetAnnotationOfType(DefaultValue.class)
                .ifPresent(dv -> {
                    for (String value : dv.value()) {
                        if (value.contains("ghcr.io/") || value.contains("@sha256:")) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    field,
                                    field.getFullName() + " has registry-ish @DefaultValue: " + value
                                )
                            );
                        }
                    }
                });
        }
    }
}
