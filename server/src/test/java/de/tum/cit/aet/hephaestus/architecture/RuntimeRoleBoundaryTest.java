package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Enforces the runtime topology invariants from ADR 0005.
 *
 * <p><b>Zero-config monolith invariant:</b> every {@code @ConditionalOnProperty} whose
 * {@code name} starts with {@link RuntimeRole#PROPERTY_PREFIX} MUST set
 * {@code matchIfMissing = true}. Otherwise a fresh JAR with no env vars set boots an
 * empty context — a DX trap identified in the principal-engineer pressure-test.
 *
 * <p><b>Environment-only @Profile invariant (advisory floor):</b> {@code @Profile} should
 * be reserved for environment names (prod/dev/test/specs/local/cds-training). Runtime
 * topology gating MUST use {@code @ConditionalOnProperty}. This test rejects the worst
 * smell — a {@code @Profile} value that's also a runtime role name — so the two gating
 * mechanisms don't drift apart.
 *
 * <p>Test runs in the {@code architecture} surefire group on every PR.
 */
@Tag("architecture")
@DisplayName("Runtime Role Boundary")
class RuntimeRoleBoundaryTest extends HephaestusArchitectureTest {

    private static final List<String> RUNTIME_ROLE_TOKENS = List.of("server", "worker", "mentor", "ingest");
    private static final List<String> ENV_PROFILE_TOKENS = List.of(
        "prod",
        "dev",
        "test",
        "specs",
        "local",
        "cds-training"
    );

    /**
     * Every {@code @ConditionalOnProperty(name = "hephaestus.runtime.…")} must include
     * {@code matchIfMissing = true} so the JAR boots full-monolith with zero env vars.
     */
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

    /**
     * Reject {@code @Profile} usages that smell like runtime-role gating. {@code @Profile}
     * is for environments; runtime roles use {@code @ConditionalOnProperty}. A
     * {@code @Profile("worker")} would silently bypass the role config + smoke-test
     * coverage.
     */
    @Test
    @DisplayName("@Profile values stay in the env vocabulary")
    void profilesAreEnvironmentsNotRuntimeRoles() {
        List<String> violations = classes.stream()
            .flatMap(clazz -> clazz.getAnnotations().stream().map(ann -> new ProfileRef(clazz, ann)))
            .filter(ProfileRef::isProfileAnnotation)
            .filter(ProfileRef::usesRuntimeRoleValue)
            .map(ProfileRef::describe)
            .collect(Collectors.toList());

        assertThat(violations)
            .as("@Profile annotations using runtime-role names instead of env names")
            .isEmpty();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private record ConditionalRef(JavaClass owner, JavaAnnotation<?> annotation) {
        boolean isConditionalOnProperty() {
            return annotation.getRawType().isEquivalentTo(ConditionalOnProperty.class);
        }

        boolean targetsRuntimeRoleProperty() {
            return propertyNames().anyMatch(n -> n.startsWith(RuntimeRole.PROPERTY_PREFIX));
        }

        boolean missingMatchIfMissingTrue() {
            Object matchIfMissing = annotation.getProperties().get("matchIfMissing");
            // Annotation default is false; we require explicit true. If the value isn't
            // captured (Spring meta-annotation default), treat as false → violation.
            return !Boolean.TRUE.equals(matchIfMissing);
        }

        String describe() {
            return owner.getFullName() + " — " + propertyNames().collect(Collectors.joining(", "));
        }

        java.util.stream.Stream<String> propertyNames() {
            java.util.stream.Stream.Builder<String> names = java.util.stream.Stream.builder();
            // name = "single" form
            Optional.ofNullable(annotation.getProperties().get("name"))
                .map(Object::toString)
                .ifPresent(names::add);
            // name = {"a","b"} array form — annotation properties expose arrays as Object[]
            Object n = annotation.getProperties().get("name");
            if (n instanceof Object[] arr) {
                for (Object element : arr) {
                    names.add(String.valueOf(element));
                }
            }
            // prefix + name combination
            Object prefix = annotation.getProperties().get("prefix");
            Object subname = annotation.getProperties().get("value");
            if (prefix != null && subname != null) {
                names.add(prefix + "." + subname);
            }
            return names.build();
        }
    }

    private record ProfileRef(JavaClass owner, JavaAnnotation<?> annotation) {
        boolean isProfileAnnotation() {
            return annotation
                .getRawType()
                .getFullName()
                .equals("org.springframework.context.annotation.Profile");
        }

        boolean usesRuntimeRoleValue() {
            Object v = annotation.getProperties().get("value");
            if (v instanceof Object[] arr) {
                for (Object element : arr) {
                    String s = String.valueOf(element).toLowerCase().replace("!", "");
                    if (RUNTIME_ROLE_TOKENS.contains(s) && !ENV_PROFILE_TOKENS.contains(s)) {
                        return true;
                    }
                }
            }
            return false;
        }

        String describe() {
            return owner.getFullName() + " — @Profile(" + java.util.Arrays.toString((Object[]) annotation.getProperties().get("value")) + ")";
        }
    }
}
