package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Enforces that every {@code @ConditionalOnProperty} gating a {@code hephaestus.runtime.*}
 * key sets {@code matchIfMissing = true}. Without this, a fresh JAR with no env vars set
 * boots an empty context — the operator becomes opt-IN to functionality instead of opt-OUT
 * to disable it.
 *
 * <p>Unwraps {@code @ConditionalOnProperty.List} containers — Spring Boot 3.5+ makes the
 * annotation {@link java.lang.annotation.Repeatable}, so multiple occurrences are wrapped
 * in a {@code .List} synthetic annotation. The test that simply matches
 * {@code @ConditionalOnProperty} without unwrapping would scan zero classes in our
 * codebase, because every site uses stacked annotations.
 *
 * <p>See ADR 0005.
 */
@Tag("architecture")
@DisplayName("Runtime Role Boundary")
class RuntimeRoleBoundaryTest extends HephaestusArchitectureTest {

    /** Container annotation Spring Boot 4 uses for repeated {@code @ConditionalOnProperty}. */
    private static final String CONDITIONAL_CONTAINER =
        "org.springframework.boot.autoconfigure.condition.ConditionalOnProperties";

    @Test
    @DisplayName("hephaestus.runtime.* gates use matchIfMissing=true")
    void runtimeGatesAreMatchIfMissingTrue() {
        List<String> violations = classes
            .stream()
            .flatMap(clazz -> conditionalOnPropertyAnnotations(clazz).map(ann -> new ConditionalRef(clazz, ann)))
            .filter(ConditionalRef::targetsRuntimeRoleProperty)
            .filter(ConditionalRef::missingMatchIfMissingTrue)
            .map(ConditionalRef::describe)
            .collect(Collectors.toList());

        assertThat(violations)
            .as("Classes with hephaestus.runtime.* gates that don't set matchIfMissing=true")
            .isEmpty();
    }

    /**
     * Returns every {@code @ConditionalOnProperty} on the class — both bare occurrences
     * and individual entries inside Spring Boot's {@code @ConditionalOnProperties} container
     * (which {@code @Repeatable} produces when multiple occurrences are stacked).
     */
    private static Stream<JavaAnnotation<?>> conditionalOnPropertyAnnotations(JavaClass clazz) {
        return clazz
            .getAnnotations()
            .stream()
            .flatMap(ann -> {
                if (ann.getRawType().isEquivalentTo(ConditionalOnProperty.class)) {
                    return Stream.of(ann);
                }
                if (ann.getRawType().getFullName().equals(CONDITIONAL_CONTAINER)) {
                    Object value = ann.getProperties().get("value");
                    if (value instanceof JavaAnnotation<?>[] arr) {
                        return Stream.of(arr);
                    }
                    if (value instanceof Object[] arr) {
                        return Stream.of(arr)
                            .filter(JavaAnnotation.class::isInstance)
                            .map(o -> (JavaAnnotation<?>) o);
                    }
                }
                return Stream.empty();
            });
    }

    private record ConditionalRef(JavaClass owner, JavaAnnotation<?> annotation) {
        boolean targetsRuntimeRoleProperty() {
            return propertyNames().anyMatch(n -> n.startsWith(RuntimeRole.PROPERTY_PREFIX));
        }

        boolean missingMatchIfMissingTrue() {
            return !Boolean.TRUE.equals(annotation.getProperties().get("matchIfMissing"));
        }

        String describe() {
            return owner.getFullName() + " — " + propertyNames().collect(Collectors.joining(", "));
        }

        Stream<String> propertyNames() {
            Stream.Builder<String> names = Stream.builder();
            Object name = annotation.getProperties().get("name");
            if (name instanceof Object[] arr) {
                for (Object element : arr) names.add(String.valueOf(element));
            } else if (name != null) {
                names.add(name.toString());
            }
            Object prefix = annotation.getProperties().get("prefix");
            Object subname = annotation.getProperties().get("value");
            if (prefix != null && subname != null) {
                names.add(prefix + "." + subname);
            }
            return names.build();
        }
    }
}
