package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.integration.connection.GitProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins #1198 AC#8 — agent-side code must not depend on the legacy
 * {@link GitProviderType} enum. Dispatch by vendor lives behind
 * {@code integration.spi.IntegrationKind} and the helper
 * {@code integration.connection.JobIntegrationKindResolver}, which is the only
 * place the legacy enum-to-kind mapping survives until the connection-cutover
 * changeset drops the column.
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
            .because(
                "#1198 AC#8: agent-side code (DiffNotePoster, PullRequestCommentPoster, "
                    + "FeedbackDeliveryService) dispatches by IntegrationKind only. Mapping from "
                    + "the legacy GitProviderType enum belongs in integration/connection/"
                    + "JobIntegrationKindResolver and disappears at connection-cutover."
            );
        rule.check(classes);
    }
}
