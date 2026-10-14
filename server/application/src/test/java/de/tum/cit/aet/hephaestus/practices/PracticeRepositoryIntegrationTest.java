package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PracticeRepositoryIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    PracticeVersionService versions;

    @Autowired
    PracticeRepository practices;

    private Practice create(String suffix) {
        var owner = persistUser("version-owner");
        var workspace = createWorkspace("version-" + suffix, "Versions", "version-org", AccountType.ORG, owner);
        return versions.create(
                workspace,
                "owned-" + suffix,
                new ItemKind("scm.pull_request"),
                DefinitionTestFixtures.definition(),
                null,
                0,
                null);
    }

    @Test
    void shouldKeepAnOffPracticeInTheTenantKindQuery() {
        Practice practice = create("query");
        practice.setFeedbackMode(FeedbackMode.OFF);
        practices.saveAndFlush(practice);
        assertThat(practices.findByWorkspaceIdAndKind(practice.getWorkspace().getId(), practice.getKind()))
                .extracting(Practice::getId)
                .contains(practice.getId());
        assertThat(practices.findByWorkspaceIdAndKind(practice.getWorkspace().getId() + 1, practice.getKind()))
                .isEmpty();
    }
}
