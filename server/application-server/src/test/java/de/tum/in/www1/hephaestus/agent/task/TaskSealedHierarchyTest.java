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
 * Guardrail against sealed-permit / {@code @JsonSubTypes} / {@code @JsonTypeName} drift on
 * {@link Task}. ArchUnit can't introspect Java 21 sealed-switch bytecode (invokedynamic against
 * {@code SwitchBootstraps.typeSwitch}), so the checks are reflection-based.
 */
@Tag("architecture")
@DisplayName("Task sealed hierarchy")
class TaskSealedHierarchyTest {

    @Test
    @DisplayName("Task is sealed")
    void taskIsSealed() {
        assertThat(Task.class.isSealed())
            .as("Task must remain sealed; adding a non-permit variant breaks every sealed switch")
            .isTrue();
    }

    @Test
    @DisplayName("permits set equals JsonSubTypes set")
    void permitsMatchJsonSubTypes() {
        Set<Class<?>> permitted = Set.of(Task.class.getPermittedSubclasses());

        JsonSubTypes subtypesAnn = Task.class.getAnnotation(JsonSubTypes.class);
        assertThat(subtypesAnn).as("Task must declare @JsonSubTypes alongside @JsonTypeInfo").isNotNull();
        Set<Class<?>> jacksonSubtypes = Arrays.stream(subtypesAnn.value())
            .map(JsonSubTypes.Type::value)
            .collect(Collectors.toSet());

        assertThat(jacksonSubtypes)
            .as(
                "Mismatch between Task.permittedSubclasses=%s and @JsonSubTypes=%s — every permit " +
                    "must register a Jackson type id and vice versa",
                permitted,
                jacksonSubtypes
            )
            .isEqualTo(permitted);
    }

    @Test
    @DisplayName("every permit has @JsonTypeName matching its @JsonSubTypes.Type(name=...)")
    void everyPermitHasMatchingJsonTypeName() {
        JsonSubTypes subtypesAnn = Task.class.getAnnotation(JsonSubTypes.class);
        assertThat(subtypesAnn).isNotNull();

        for (JsonSubTypes.Type entry : subtypesAnn.value()) {
            Class<?> subtype = entry.value();
            String supertypeName = entry.name();
            JsonTypeName typeNameAnn = subtype.getAnnotation(JsonTypeName.class);
            assertThat(typeNameAnn)
                .as(
                    "Permit %s must declare @JsonTypeName(\"%s\") so the type id stays " +
                        "single-sourced with the subtype",
                    subtype.getName(),
                    supertypeName
                )
                .isNotNull();
            assertThat(typeNameAnn.value())
                .as(
                    "Permit %s declares @JsonTypeName(\"%s\") but Task's @JsonSubTypes registers " +
                        "it as \"%s\" — the two must match",
                    subtype.getName(),
                    typeNameAnn.value(),
                    supertypeName
                )
                .isEqualTo(supertypeName);
        }
    }
}
