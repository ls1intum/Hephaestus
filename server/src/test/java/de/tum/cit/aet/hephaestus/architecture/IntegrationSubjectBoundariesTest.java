package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins #1198 pass-14: the bare {@code OrganizationRepository#findByLoginIgnoreCase(String)}
 * method (no provider scoping) must never be reintroduced.
 *
 * <p>Background: pre-pass-14, a `HephaestusTest` org on github.com and a `hephaestustest`
 * group on gitlab.lrz.de got fused into one row by unscoped case-insensitive lookup.
 * The fix removed the unscoped method and migrated every call site to
 * {@code findByLoginIgnoreCaseAndProviderId} or
 * {@code findByLoginIgnoreCaseAndProvider_Type}. This rule prevents regression.
 *
 * <p>See ADR-0012 — Cross-instance identity safety on sync paths.
 */
class IntegrationSubjectBoundariesTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("OrganizationRepository must not declare unscoped findByLogin* (provider-id required)")
    void noUnscopedFindByLogin() {
        ArchRule rule = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .haveSimpleName("OrganizationRepository")
            .and()
            .haveNameStartingWith("findByLogin")
            .should()
            .haveRawParameterTypes(String.class)
            .because(
                "ADR-0012 — provider-scoped lookups only. Unscoped findByLogin*(String) "
                    + "creates cross-instance identity-fusion (HephaestusTest on github.com "
                    + "vs hephaestustest on gitlab.lrz.de get merged into one row). Use "
                    + "findByLoginIgnoreCaseAndProviderId or findByLoginIgnoreCaseAndProvider_Type."
            );
        rule.check(classes);
    }
}
