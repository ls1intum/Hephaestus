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
 * <p>See ADR 0005.
 */
@Tag("architecture")
@DisplayName("Runtime Role Boundary")
class RuntimeRoleBoundaryTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("hephaestus.runtime.* gates use matchIfMissing=true")
    void runtimeGatesAreMatchIfMissingTrue() {
        List<String> violations = classes.stream()
            .flatMap(clazz -> clazz.getAnnotations().stream().map(ann -> new ConditionalRef(clazz, ann)))
            .filter(ConditionalRef::isConditionalOnProperty)
            .filter(ConditionalRef::targetsRuntimeRoleProperty)
            .filter(ConditionalRef::missingMatchIfMissingTrue)
            .map(ConditionalRef::describe)
            .collect(Collectors.toList());

        assertThat(violations)
            .as("Classes with hephaestus.runtime.* gates that don't set matchIfMissing=true")
            .isEmpty();
    }

    private record ConditionalRef(JavaClass owner, JavaAnnotation<?> annotation) {
        boolean isConditionalOnProperty() {
            return annotation.getRawType().isEquivalentTo(ConditionalOnProperty.class);
        }

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
