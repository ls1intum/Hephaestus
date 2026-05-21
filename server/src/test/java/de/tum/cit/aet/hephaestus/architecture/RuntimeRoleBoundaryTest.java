package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pins two invariants for {@code hephaestus.runtime.*} gates:
 *
 * <ol>
 *   <li>Every gate sets {@code matchIfMissing=true}. Without this, a fresh JAR with no env vars
 *       set boots an empty context — operators become opt-IN to functionality instead of opt-OUT
 *       to disable it.</li>
 *   <li>The classes the gate is supposed to cover actually carry the right
 *       {@code @ConditionalOnProperty}. Drift between {@code RuntimeRole} javadoc and the live
 *       annotations is the bug class this enforces.</li>
 * </ol>
 *
 * <p>Unwraps {@code @ConditionalOnProperty.List} — Spring Boot 3.5+ makes the annotation
 * {@link java.lang.annotation.Repeatable}, so multiple occurrences are wrapped in a {@code .List}
 * synthetic annotation.
 *
 * <p>See ADR 0005 (baseline) and ADR 0008 (webhook role + SERVER_PROPERTY wiring).
 */
@Tag("architecture")
class RuntimeRoleBoundaryTest extends HephaestusArchitectureTest {

    /** Container annotation Spring Boot 4 uses for repeated {@code @ConditionalOnProperty}. */
    private static final String CONDITIONAL_CONTAINER =
        "org.springframework.boot.autoconfigure.condition.ConditionalOnProperties";

    /**
     * Single property-gated config per role. Controllers inside {@code gitprovider.webhook} are
     * implicitly gated via {@code @ConditionalOnBean(JetStreamPublisher.class)} — they auto-load
     * iff {@link de.tum.cit.aet.hephaestus.gitprovider.webhook.WebhookConfiguration} loads, so
     * listing them here would just duplicate the WebhookConfiguration gate.
     */
    private static final Map<String, String> EXPECTED_GATES = Map.of(
        "de.tum.cit.aet.hephaestus.gitprovider.webhook.WebhookConfiguration",
        RuntimeRole.WEBHOOK_PROPERTY,
        "de.tum.cit.aet.hephaestus.core.runtime.ServerSchedulingConfig",
        RuntimeRole.SERVER_PROPERTY,
        "de.tum.cit.aet.hephaestus.gitprovider.sync.NatsConsumerService",
        RuntimeRole.SERVER_PROPERTY,
        "de.tum.cit.aet.hephaestus.workspace.WorkspaceStartupListener",
        RuntimeRole.SERVER_PROPERTY
    );

    @Test
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

    @Test
    void enableSchedulingLivesOnlyOnServerSchedulingConfig() {
        List<String> hosts = classes
            .stream()
            .filter(c -> c.getFullName().startsWith("de.tum.cit.aet.hephaestus."))
            .filter(c -> c.isAnnotatedWith(EnableScheduling.class) || c.isMetaAnnotatedWith(EnableScheduling.class))
            .map(JavaClass::getFullName)
            .collect(Collectors.toList());

        assertThat(hosts)
            .as(
                "@EnableScheduling (direct or meta-annotated) must appear on exactly one class (ServerSchedulingConfig); " +
                    "any other @Scheduled host relies on its absence on the webhook role to no-op silently — keep that invariant load-bearing."
            )
            .containsExactly("de.tum.cit.aet.hephaestus.core.runtime.ServerSchedulingConfig");
    }

    @Test
    void webhookPackageIsIsolatedFromServerWorkerConcerns() {
        noClasses()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.gitprovider.webhook..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "de.tum.cit.aet.hephaestus.workspace..",
                "de.tum.cit.aet.hephaestus.leaderboard..",
                "de.tum.cit.aet.hephaestus.agent.."
            )
            .because(
                "webhook receiver is a pure publish-only role; depending on workspace/leaderboard/agent would re-introduce " +
                    "the wiring leaks runtime testing already exposed (ObjectProvider cascade, etc.) and break role isolation"
            )
            .check(classes);
    }

    @Test
    void expectedRuntimeGatesArePresent() {
        EXPECTED_GATES.forEach((fqn, expectedProperty) -> {
            JavaClass clazz = classes
                .stream()
                .filter(c -> c.getFullName().equals(fqn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected class not found in ArchUnit scan: " + fqn));
            boolean matched = conditionalOnPropertyAnnotations(clazz)
                .map(ann -> new ConditionalRef(clazz, ann))
                .anyMatch(ref -> ref.propertyNames().anyMatch(name -> name.equals(expectedProperty)));
            assertThat(matched).as("%s must be gated by @ConditionalOnProperty('%s')", fqn, expectedProperty).isTrue();
        });
    }

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
