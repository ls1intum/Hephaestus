package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for {@link WorkspaceResolver}.
 */
class WorkspaceResolverTest extends BaseUnitTest {

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private WorkspaceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new WorkspaceResolver(repositoryToMonitorRepository, workspaceRepository);
    }

    private Workspace createWorkspace(String slug) {
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug(slug);
        return workspace;
    }

    private RepositoryToMonitor createMonitor(Workspace workspace) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setWorkspace(workspace);
        return monitor;
    }

    @Nested
    @DisplayName("Null and empty input handling")
    class NullInputTests {

        @Test
        @DisplayName("Should return empty for null nameWithOwner")
        void returnsEmptyForNull() {
            Optional<Workspace> result = resolver.resolveForRepository(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(repositoryToMonitorRepository, workspaceRepository);
        }
    }

    @Nested
    @DisplayName("Authoritative resolution (monitor lookup)")
    class AuthoritativeResolutionTests {

        @Test
        @DisplayName("Should resolve workspace from monitor configuration")
        void resolvesFromMonitor() {
            Workspace workspace = createWorkspace("test-workspace");
            RepositoryToMonitor monitor = createMonitor(workspace);
            when(repositoryToMonitorRepository.findByNameWithOwner("ls1intum/Hephaestus")).thenReturn(
                Optional.of(monitor)
            );

            Optional<Workspace> result = resolver.resolveForRepository("ls1intum/Hephaestus");

            assertThat(result).isPresent();
            assertThat(result.get().getWorkspaceSlug()).isEqualTo("test-workspace");
            // Should not fall through to heuristic lookup
            verifyNoInteractions(workspaceRepository);
        }

        @Test
        @DisplayName("Should return empty when monitor exists but workspace is null")
        void returnsEmptyForMonitorWithNullWorkspace() {
            RepositoryToMonitor monitor = createMonitor(null);
            when(repositoryToMonitorRepository.findByNameWithOwner("ls1intum/Hephaestus")).thenReturn(
                Optional.of(monitor)
            );

            Optional<Workspace> result = resolver.resolveForRepository("ls1intum/Hephaestus");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Heuristic resolution (owner login fallback)")
    class HeuristicResolutionTests {

        @Test
        @DisplayName("Should fall back to owner-based lookup when no monitor exists")
        void fallsBackToOwnerLookup() {
            when(repositoryToMonitorRepository.findByNameWithOwner("ls1intum/Hephaestus")).thenReturn(Optional.empty());
            Workspace workspace = createWorkspace("ls1intum-workspace");
            when(workspaceRepository.findByAccountLoginIgnoreCase("ls1intum")).thenReturn(Optional.of(workspace));

            Optional<Workspace> result = resolver.resolveForRepository("ls1intum/Hephaestus");

            assertThat(result).isPresent();
            assertThat(result.get().getWorkspaceSlug()).isEqualTo("ls1intum-workspace");
        }

        @Test
        @DisplayName("Should return empty when no monitor and no owner match")
        void returnsEmptyWhenNoMatch() {
            when(repositoryToMonitorRepository.findByNameWithOwner("unknown/repo")).thenReturn(Optional.empty());
            when(workspaceRepository.findByAccountLoginIgnoreCase("unknown")).thenReturn(Optional.empty());

            Optional<Workspace> result = resolver.resolveForRepository("unknown/repo");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for input without slash")
        void returnsEmptyForNoSlash() {
            when(repositoryToMonitorRepository.findByNameWithOwner("noslash")).thenReturn(Optional.empty());

            Optional<Workspace> result = resolver.resolveForRepository("noslash");

            assertThat(result).isEmpty();
            verifyNoInteractions(workspaceRepository);
        }

        @Test
        @DisplayName("Should skip heuristic when owner is empty (leading slash)")
        void skipsHeuristicForEmptyOwner() {
            when(repositoryToMonitorRepository.findByNameWithOwner("/repo")).thenReturn(Optional.empty());

            Optional<Workspace> result = resolver.resolveForRepository("/repo");

            assertThat(result).isEmpty();
            // Should NOT query workspaceRepository with empty string
            verifyNoInteractions(workspaceRepository);
        }
    }
}
