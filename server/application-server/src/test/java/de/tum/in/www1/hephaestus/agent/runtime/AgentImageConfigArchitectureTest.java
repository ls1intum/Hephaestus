package de.tum.in.www1.hephaestus.agent.runtime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.tum.in.www1.hephaestus.architecture.HephaestusArchitectureTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

class AgentImageConfigArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void onlyAgentImagePropertiesMayHardCodeARegistryReference() {
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
