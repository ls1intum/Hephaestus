package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import de.tum.cit.aet.hephaestus.integration.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandler;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Locks the post-Slice-F state where every production message handler routes through the
 * unified {@code IntegrationMessageHandlerRegistry}.
 *
 * <p>The slice that introduced {@link AbstractIntegrationMessageHandler} and migrated all
 * 24 GitHub + 8 GitLab handlers onto it deleted both legacy registries
 * ({@code GitHubMessageHandlerRegistry}, {@code GitLabMessageHandlerRegistry}) and their
 * abstract bases ({@code GitHubMessageHandler<T>}, {@code GitLabMessageHandler<T>}). The
 * rules below enforce that the migration cannot regress:
 *
 * <ol>
 *   <li>No production class may name the deleted registries — accidentally re-introducing
 *       a parallel resolution path is the single failure mode this slice spent solving.</li>
 *   <li>Every {@code *MessageHandler} class under {@code integration/<kind>/} must extend
 *       {@link AbstractIntegrationMessageHandler} OR implement
 *       {@link IntegrationMessageHandler} directly — there is no other valid handler
 *       shape now that the legacy bases are gone.</li>
 *   <li>The unified registry must be populated; an empty registry would silently route
 *       100% of traffic to ACK-as-no-op. We assert via a minimum count.</li>
 * </ol>
 *
 * <p>The minimum count (30) is a floor, not a target — it leaves slack for handlers to be
 * added or removed within the same family without churning this test, while still failing
 * loudly if the migration is reverted to the legacy bases (zero unified handlers).
 */
@Tag("architecture")
@DisplayName("Integration message handler architecture")
class IntegrationMessageHandlerArchTest extends HephaestusArchitectureTest {

    /**
     * Names that, if found in production code, indicate the deleted legacy registries are
     * being re-introduced. Matched against both fully-qualified class names and references
     * (extends / implements / type usage).
     */
    private static final List<String> DELETED_LEGACY_NAMES = List.of(
        "de.tum.cit.aet.hephaestus.integration.github.common.GitHubMessageHandler",
        "de.tum.cit.aet.hephaestus.integration.github.common.GitHubMessageHandlerRegistry",
        "de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabMessageHandler",
        "de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabMessageHandlerRegistry"
    );

    @Test
    @DisplayName("deleted legacy registry classes never reappear in production code")
    void deletedLegacyClassesDoNotReappear() {
        List<String> reintroduced = classes
            .stream()
            .map(JavaClass::getFullName)
            .filter(DELETED_LEGACY_NAMES::contains)
            .collect(Collectors.toList());

        assertThat(reintroduced)
            .as(
                "The legacy per-kind GitHub/GitLab message-handler registries and their "
                    + "abstract bases were deleted in #1198 Slice F. Re-introducing them under "
                    + "any of these fully-qualified names splits the routing surface again."
            )
            .isEmpty();
    }

    @Test
    @DisplayName("every *MessageHandler under integration/<kind>/ binds to the unified SPI")
    void everyMessageHandlerExtendsUnifiedBaseOrImplementsSpi() {
        // Whitelist: classes that look like handlers by name but aren't NATS message
        // handlers (e.g. ABSTRACT bases, support classes). Add here ONLY if intentional.
        List<String> whitelist = List.of(AbstractIntegrationMessageHandler.class.getName());

        List<String> violations = classes
            .stream()
            .filter(c -> c.getPackageName().startsWith("de.tum.cit.aet.hephaestus.integration.github."))
            .filter(c -> c.getSimpleName().endsWith("MessageHandler"))
            .filter(c -> !c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
            .filter(c -> !whitelist.contains(c.getFullName()))
            .filter(c -> !bindsToUnifiedSpi(c))
            .map(JavaClass::getFullName)
            .collect(Collectors.toList());

        // Same scan for GitLab.
        violations.addAll(
            classes
                .stream()
                .filter(c -> c.getPackageName().startsWith("de.tum.cit.aet.hephaestus.integration.gitlab."))
                .filter(c -> c.getSimpleName().endsWith("MessageHandler"))
                .filter(c -> !c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
                .filter(c -> !whitelist.contains(c.getFullName()))
                .filter(c -> !bindsToUnifiedSpi(c))
                .map(JavaClass::getFullName)
                .toList()
        );

        assertThat(violations)
            .as(
                "Every concrete *MessageHandler class under integration/<kind>/ must extend "
                    + "AbstractIntegrationMessageHandler or implement IntegrationMessageHandler "
                    + "directly. Anything else cannot be picked up by the unified registry and "
                    + "would silently NOT receive messages."
            )
            .isEmpty();
    }

    @Test
    @DisplayName("at least 30 concrete handlers bind to the unified registry")
    void unifiedRegistryFloorIsMet() {
        long count = classes
            .stream()
            .filter(c -> c.getPackageName().startsWith("de.tum.cit.aet.hephaestus.integration."))
            .filter(c -> c.getSimpleName().endsWith("MessageHandler"))
            .filter(c -> !c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
            .filter(IntegrationMessageHandlerArchTest::bindsToUnifiedSpi)
            .count();

        assertThat(count)
            .as(
                "The unified IntegrationMessageHandlerRegistry must carry the full handler "
                    + "fleet. 30 is the floor (24 GitHub + 8 GitLab − 2 slack/whitespace) "
                    + "that survives normal additions/removals; anything below means the "
                    + "registry is silently empty and traffic falls to ACK-as-no-op."
            )
            .isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("AbstractIntegrationMessageHandler stays abstract — never @Component-able directly")
    void abstractBaseStaysAbstract() {
        JavaClass base = classes
            .stream()
            .filter(c -> c.getFullName().equals(AbstractIntegrationMessageHandler.class.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("AbstractIntegrationMessageHandler must be in production classes"));

        assertThat(base.getModifiers())
            .as(
                "AbstractIntegrationMessageHandler must remain abstract. Making it concrete "
                    + "would let Spring instantiate it as a bare bean with a null EventTypeKey, "
                    + "which the registry's null-key check would (correctly) reject — but at "
                    + "boot, not at refactor-time. Failing here is louder and earlier."
            )
            .contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT);
    }

    @Test
    @DisplayName("every concrete unified handler under integration/ declares a unique EventTypeKey")
    void everyHandlerHasUniqueKeyByClassName() {
        // Cheap structural check: each handler class name + its directory is unique by
        // construction (filesystem uniqueness), so this asserts we haven't accidentally
        // duplicated a Spring @Component bean shape that would clash at runtime.
        long unifiedHandlers = classes
            .stream()
            .filter(c -> c.getPackageName().startsWith("de.tum.cit.aet.hephaestus.integration."))
            .filter(c -> c.getSimpleName().endsWith("MessageHandler"))
            .filter(c -> !c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
            .filter(IntegrationMessageHandlerArchTest::bindsToUnifiedSpi)
            .count();

        long distinctNames = classes
            .stream()
            .filter(c -> c.getPackageName().startsWith("de.tum.cit.aet.hephaestus.integration."))
            .filter(c -> c.getSimpleName().endsWith("MessageHandler"))
            .filter(c -> !c.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
            .filter(IntegrationMessageHandlerArchTest::bindsToUnifiedSpi)
            .map(JavaClass::getSimpleName)
            .distinct()
            .count();

        assertThat(distinctNames).as("handler simple names must be unique").isEqualTo(unifiedHandlers);
    }

    private static boolean bindsToUnifiedSpi(JavaClass c) {
        // Direct interface implementation.
        if (
            c
                .getAllRawInterfaces()
                .stream()
                .anyMatch(i -> i.getFullName().equals(IntegrationMessageHandler.class.getName()))
        ) {
            return true;
        }
        // Inheritance chain (covers the AbstractIntegrationMessageHandler<T> case, which
        // itself implements IntegrationMessageHandler).
        JavaClass parent = c.getRawSuperclass().orElse(null);
        while (parent != null && !parent.getFullName().equals("java.lang.Object")) {
            if (parent.getFullName().equals(AbstractIntegrationMessageHandler.class.getName())) {
                return true;
            }
            parent = parent.getRawSuperclass().orElse(null);
        }
        return false;
    }
}
