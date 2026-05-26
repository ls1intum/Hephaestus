package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bare {@code OrganizationRepository#findByLoginIgnoreCase(String)} (no provider
 * scoping) MUST NOT be reintroduced — it fuses same-named orgs across providers
 * (e.g. {@code HephaestusTest} on github.com vs {@code hephaestustest} on gitlab.lrz.de)
 * into one row. Use {@code findByLoginIgnoreCaseAndProviderId} or
 * {@code findByLoginIgnoreCaseAndProvider_Type}. See ADR-0012.
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
