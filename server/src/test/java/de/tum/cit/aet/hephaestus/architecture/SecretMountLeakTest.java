package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Forbids log statements in the {@code agent.*} packages from formatting
 * {@code SecretMount} instances or any field whose name matches {@code secret*}/{@code *secret}.
 *
 * <p>Defense in depth alongside {@code SecretMount.toString()} redaction. Even though the
 * sealed-record toString redacts content, a future refactor could (a) construct a custom
 * formatter, (b) reflectively read the {@code data} field, or (c) accidentally pass a
 * Map containing the secret to a logger.format call. This test catches any of those at
 * PR time.
 *
 * <p>Today {@code SecretMount} isn't yet exercised by any production code path (waiting
 * for K8s adapter epic), but the guard ships now so the rule is already enforced when the
 * first real consumer lands.
 */
@Tag("architecture")
@DisplayName("Secret Mount Log Leak")
class SecretMountLeakTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("agent.* does not log SecretMount instances")
    void agentDoesNotLogSecretMount() {
        DescribedPredicate<JavaCall<?>> passesSecretMountToLogger =
            new DescribedPredicate<>("passes SecretMount to a logging API") {
                @Override
                public boolean test(JavaCall<?> call) {
                    boolean targetIsLogger =
                        call.getTargetOwner().getFullName().startsWith("org.slf4j.")
                            || call.getTargetOwner().getFullName().startsWith("java.util.logging.");
                    if (!targetIsLogger) return false;
                    return call.getTarget()
                        .getRawParameterTypes()
                        .stream()
                        .anyMatch(p -> p.getFullName().endsWith(".SecretMount"));
                }
            };

        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..agent..")
            .should()
            .callMethodWhere(passesSecretMountToLogger)
            .because(
                "SecretMount.toString() redacts content, but a logger.info(\"...{}\", secret) "
                + "call would still pass the redacted toString — which is fine. The real risk "
                + "is logger calls that take SecretMount as a parameter without going through "
                + "toString (e.g., formatter binding). This rule rejects passing SecretMount "
                + "to logging APIs at all. Use logger.info(\"keys={}\", secret.data().keySet()) "
                + "if you need to log key names."
            );

        rule.check(classes);
    }
}
