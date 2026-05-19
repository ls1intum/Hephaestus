package de.tum.in.www1.hephaestus.agent.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guardrail against {@code permits} / {@code @JsonSubTypes} / {@code @JsonTypeName} drift on
 * {@link Task}. ArchUnit can't introspect Java 21 sealed-switch bytecode, so this is reflection-based.
 */
@Tag("architecture")
@DisplayName("Task sealed hierarchy")
class TaskSealedHierarchyTest {

    @Test
    @DisplayName("Task permits, @JsonSubTypes and per-permit @JsonTypeName all agree")
    void permitsJsonSubtypesAndJsonTypeNameAgree() {
        assertThat(Task.class.isSealed()).as("Task must remain sealed").isTrue();

        JsonSubTypes subtypesAnn = Task.class.getAnnotation(JsonSubTypes.class);
        assertThat(subtypesAnn).as("Task must declare @JsonSubTypes").isNotNull();
        Set<Class<?>> jacksonSubtypes = Arrays.stream(subtypesAnn.value())
            .map(JsonSubTypes.Type::value)
            .collect(Collectors.toSet());

        assertThat(jacksonSubtypes).isEqualTo(Set.of(Task.class.getPermittedSubclasses()));

        for (JsonSubTypes.Type entry : subtypesAnn.value()) {
            JsonTypeName typeNameAnn = entry.value().getAnnotation(JsonTypeName.class);
            assertThat(typeNameAnn).as("%s must declare @JsonTypeName", entry.value()).isNotNull();
            assertThat(typeNameAnn.value()).isEqualTo(entry.name());
        }
    }
}
