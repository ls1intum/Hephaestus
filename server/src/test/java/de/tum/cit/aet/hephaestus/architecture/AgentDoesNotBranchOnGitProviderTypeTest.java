package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Agent-side code must dispatch by {@link de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind}
 * only — never by the legacy {@link GitProviderType} enum.
 */
class AgentDoesNotBranchOnGitProviderTypeTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("agent/** must not depend on GitProviderType")
    void agentDoesNotImportGitProviderType() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..agent..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(GitProviderType.class.getName())
            .because("agent/** dispatches by IntegrationKind only");
        rule.check(classes);
    }
}
