package de.tum.cit.aet.hephaestus.integration.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class SlackHephaestusUiLinksTest extends BaseUnitTest {

    @Mock
    private WorkspaceSummaryQuery workspaceSummaryQuery;

    @Test
    void workspaceHomeUrl_usesWorkspaceSlug() {
        when(workspaceSummaryQuery.findById(7L)).thenReturn(
            Optional.of(new WorkspaceSummaryQuery.WorkspaceSummary(7L, "team-alpha", "Team Alpha", null))
        );

        SlackHephaestusUiLinks links = new SlackHephaestusUiLinks(workspaceSummaryQuery, "https://heph.example/");

        assertThat(links.workspaceHomeUrl(7L)).isEqualTo("https://heph.example/w/team-alpha");
    }

    @Test
    void workspaceHomeUrl_fallsBackToConfiguredRoot() {
        when(workspaceSummaryQuery.findById(7L)).thenReturn(Optional.empty());

        SlackHephaestusUiLinks links = new SlackHephaestusUiLinks(workspaceSummaryQuery, "https://heph.example/");

        assertThat(links.workspaceHomeUrl(7L)).isEqualTo("https://heph.example");
    }

    @Test
    void userSettingsUrl_linksToPersonalSettings() {
        SlackHephaestusUiLinks links = new SlackHephaestusUiLinks(workspaceSummaryQuery, "https://heph.example/");

        assertThat(links.userSettingsUrl()).isEqualTo("https://heph.example/settings");
    }
}
