package de.tum.cit.aet.hephaestus.agent.runtime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.tum.cit.aet.hephaestus.architecture.HephaestusArchitectureTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

class AgentImageConfigArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void noConfigurationDefaultMayHardCodeARegistryReference() {
        constructors()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(ConfigurationProperties.class)
            .should(new RegistryReferenceCondition())
            .because(
                "ADR 0031 — a compiled-in image reference cannot know which build the deployment is running, " +
                    "so the only correct value follows the deployment's own image tag from application.yml"
            )
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
