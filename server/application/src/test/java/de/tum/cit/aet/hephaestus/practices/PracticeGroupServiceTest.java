package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PracticeGroupServiceTest extends BaseUnitTest {
    @Test
    void shouldClearOptionalGroupFieldsWhenTheReplacementContainsNull() {
        var repository = mock(PracticeGroupRepository.class);
        var workspaces = mock(WorkspaceRepository.class);
        var workspace = new Workspace();
        workspace.setId(10L);
        var group = new PracticeGroup();
        group.setId(20L);
        group.setName("Old name");
        group.setDescription("Old description");
        when(workspaces.findByIdForUpdate(10L)).thenReturn(Optional.of(workspace));
        when(repository.findByWorkspaceIdAndSlug(10L, "reviews")).thenReturn(Optional.of(group));
        var service = new PracticeGroupService(
                repository, mock(PracticeRepository.class), workspaces, mock(ConfigAuditPort.class));
        var saved = service.updateGroup(
                new WorkspaceContext(10L, "workspace", "Workspace", null, null, Set.of()),
                "reviews",
                new GroupAttributes("New name", null, null, null, null),
                false);
        assertThat(saved.getName()).isEqualTo("New name");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.isShownToDevelopers()).isFalse();
    }
}
