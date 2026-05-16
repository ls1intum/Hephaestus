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
 * Guardrail for the sealed {@link Task} hierarchy. Three independent drift checks:
 *
 * <ol>
 *   <li>{@code Task} stays {@code sealed} — javac alone enforces exhaustiveness at every sealed
 *       switch, but only if the seal survives.</li>
 *   <li>Every {@link JsonSubTypes.Type} entry on {@code Task} has a matching permit and vice
 *       versa — a new permit added without registering its Jackson type id would serialise
 *       fine but fail polymorphic deserialisation.</li>
 *   <li>Every permit carries {@link JsonTypeName} matching its {@code @JsonSubTypes.Type(name=...)}
 *       — keeps the type-id source of truth co-located with the subtype itself, so subtype-side
 *       lookups (e.g. annotation introspection in a producer registry) don't silently disagree
 *       with the supertype's {@code @JsonSubTypes} table.</li>
 * </ol>
 *
 * <p>Java 21 sealed-switch bytecode is {@code invokedynamic} against {@code
 * SwitchBootstraps.typeSwitch} and is opaque to ArchUnit 1.3.x — this test is reflection-based
 * and serves as a structural guardrail, not a dispatch-site inspector.
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
