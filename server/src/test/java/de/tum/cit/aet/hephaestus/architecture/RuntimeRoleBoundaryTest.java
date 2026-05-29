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
class RuntimeRoleBoundaryTest extends HephaestusArchitectureTest {

    /** Container annotation Spring Boot 4 uses for repeated {@code @ConditionalOnProperty}. */
    private static final String CONDITIONAL_CONTAINER =
        "org.springframework.boot.autoconfigure.condition.ConditionalOnProperties";

    /** SpEL-based conditional; scanned textually for retired-flag references. */
    private static final String CONDITIONAL_ON_EXPRESSION =
        "org.springframework.boot.autoconfigure.condition.ConditionalOnExpression";

    /**
     * Single property-gated config per role. Controllers inside {@code integration.webhook} are
     * implicitly gated via {@code @ConditionalOnBean(JetStreamPublisher.class)} — they auto-load
     * iff {@link de.tum.cit.aet.hephaestus.integration.core.webhook.WebhookConfiguration} loads, so
     * listing them here would just duplicate the WebhookConfiguration gate.
     */
    private static final Map<String, String> EXPECTED_GATES = Map.of(
        "de.tum.cit.aet.hephaestus.integration.core.webhook.WebhookConfiguration",
        RuntimeRole.WEBHOOK_PROPERTY,
        "de.tum.cit.aet.hephaestus.core.runtime.ServerSchedulingConfig",
        RuntimeRole.SERVER_PROPERTY,
        "de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer",
        RuntimeRole.SERVER_PROPERTY,
        "de.tum.cit.aet.hephaestus.workspace.WorkspaceStartupListener",
        RuntimeRole.SERVER_PROPERTY,
        "de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerConfiguration",
        RuntimeRole.WORKER_PROPERTY,
        "de.tum.cit.aet.hephaestus.agent.sandbox.docker.DockerSandboxConfiguration",
        RuntimeRole.WORKER_PROPERTY,
        "de.tum.cit.aet.hephaestus.agent.sandbox.docker.AgentImagePullBootstrapper",
        RuntimeRole.WORKER_PROPERTY,
        "de.tum.cit.aet.hephaestus.core.runtime.hub.HubConfiguration",
        RuntimeRole.SERVER_PROPERTY,
        "de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenExchangeController",
        RuntimeRole.SERVER_PROPERTY
    );

    /**
     * Beans that must wire <em>unconditionally</em> (no {@code @ConditionalOnProperty}): mentoring
     * is always-on; per-workspace enablement lives in the DB ({@code WorkspaceFeatures.mentor_enabled}),
     * not in a capability flag. These previously carried a (now-removed) {@code hephaestus.sandbox.enabled}
     * gate — see the group-1 config-cohesion change.
     */
    private static final List<String> UNCONDITIONAL_MENTOR_BEANS = List.of(
        "de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatService",
        "de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatController",
        "de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatExecutorConfig",
        "de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatMetrics"
    );

    @Test
    void noStackedConditionalOnPropertyOnSameElement() {
        // Spring honors only ONE @ConditionalOnProperty per element; the second annotation is
        // silently ignored. Anyone wanting both conditions must use @ConditionalOnExpression or
        // compose with @Conditional.
        List<String> violations = classes
            .stream()
            .filter(c -> c.getFullName().startsWith("de.tum.cit.aet.hephaestus."))
            .filter(
                clazz ->
                    clazz
                        .getAnnotations()
                        .stream()
                        .filter(a -> a.getRawType().isEquivalentTo(ConditionalOnProperty.class))
                        .count() >
                    1
            )
            .map(JavaClass::getFullName)
            .collect(Collectors.toList());

        assertThat(violations)
            .as(
                "Classes with multiple @ConditionalOnProperty annotations — Spring honors only the " +
                    "first; use @ConditionalOnExpression for compound predicates instead."
            )
            .isEmpty();
    }

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
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.core.webhook..")
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

    @Test
    void mentorBeansWireUnconditionally() {
        List<String> stillGated = UNCONDITIONAL_MENTOR_BEANS.stream()
            .map(fqn ->
                classes
                    .stream()
                    .filter(c -> c.getFullName().equals(fqn))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected class not found in ArchUnit scan: " + fqn))
            )
            .filter(clazz -> conditionalOnPropertyAnnotations(clazz).findAny().isPresent())
            .map(JavaClass::getFullName)
            .collect(Collectors.toList());

        assertThat(stillGated)
            .as(
                "Mentor beans must wire unconditionally — mentoring is always-on (per-workspace enable " +
                    "lives in WorkspaceFeatures.mentor_enabled), not behind a capability flag."
            )
            .isEmpty();
    }

    @Test
    void noBeanIsGatedOnTheRetiredSandboxEnabledFlag() {
        // hephaestus.sandbox.enabled was a conflated capability flag: the Docker sandbox IS the
        // worker role (now gated on WORKER_PROPERTY), and mentoring is always-on. The flag is gone;
        // nothing — @ConditionalOnProperty or @ConditionalOnExpression — may reference it again.
        List<String> violations = classes
            .stream()
            .filter(c -> c.getFullName().startsWith("de.tum.cit.aet.hephaestus."))
            .filter(clazz -> referencesRetiredSandboxFlag(clazz))
            .map(JavaClass::getFullName)
            .collect(Collectors.toList());

        assertThat(violations).as("No bean may be gated on the retired hephaestus.sandbox.enabled flag").isEmpty();
    }

    private static boolean referencesRetiredSandboxFlag(JavaClass clazz) {
        boolean viaProperty = conditionalOnPropertyAnnotations(clazz)
            .map(ann -> new ConditionalRef(clazz, ann))
            .anyMatch(ref -> ref.propertyNames().anyMatch(name -> name.equals("hephaestus.sandbox.enabled")));
        if (viaProperty) {
            return true;
        }
        // @ConditionalOnExpression carries a single String SpEL value — scan it textually so a
        // re-introduced compound gate (e.g. "${hephaestus.sandbox.enabled} and ...") is caught too.
        return clazz
            .getAnnotations()
            .stream()
            .filter(a -> a.getRawType().getFullName().equals(CONDITIONAL_ON_EXPRESSION))
            .map(a -> String.valueOf(a.getProperties().get("value")))
            .anyMatch(expr -> expr.contains("hephaestus.sandbox.enabled"));
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
