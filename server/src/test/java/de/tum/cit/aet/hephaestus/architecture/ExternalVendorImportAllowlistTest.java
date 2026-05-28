package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the allowlist of non-{@code integration/} classes that import vendor
 * adapter packages. The only documented exception is Jackson polymorphic-
 * deserialization mixins, which must bind against the vendor types directly
 * and so cannot reach them through a SPI port. Adding without removing fails
 * the build.
 */
class ExternalVendorImportAllowlistTest extends HephaestusArchitectureTest {

    private static final Set<String> ALLOWED_CALLERS = Set.of(
        // Jackson polymorphic-deserialization mixins for GitHub GraphQL SDK types.
        // Cannot be SPI-laundered: Jackson resolves type IDs against the vendor
        // types themselves at bind time. Direction-of-travel: relocate into
        // integration.scm.github.graphql.mixin in a future cleanup.
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubActorMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubIssueMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2FieldConfigurationMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2ItemContentMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2ItemFieldValueMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2OwnerMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubPullRequestMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubRepositoryOwnerMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubRequestedReviewerMixin"
    );

    @Test
    @DisplayName("no new class outside integration/ imports a vendor adapter package")
    void noNewExternalVendorImporter() {
        ArchRule rule = noClasses()
            .that(
                new DescribedPredicate<>("are not in integration/ and are not in the allowlist") {
                    @Override
                    public boolean test(JavaClass input) {
                        String pkg = input.getPackageName();
                        if (pkg.startsWith("de.tum.cit.aet.hephaestus.integration")) {
                            return false; // intra-integration imports are governed by other rules
                        }
                        return !ALLOWED_CALLERS.contains(input.getFullName());
                    }
                }
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "de.tum.cit.aet.hephaestus.integration.scm.github..",
                "de.tum.cit.aet.hephaestus.integration.scm.gitlab..",
                "de.tum.cit.aet.hephaestus.integration.slack..",
                "de.tum.cit.aet.hephaestus.integration.outline.."
            )
            .because(
                "External modules reach vendor adapters through SPI ports in integration.core.spi " +
                    "or via Spring ApplicationEvents. Allowlist is exhaustive — adding without " +
                    "removing fails the build."
            );
        rule.check(classes);
    }
}
