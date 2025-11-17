package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({ MockitoExtension.class, GitHubPayloadExtension.class })
class GitHubPushMessageHandlerTest {

    @Mock
    private GitHubRepositorySyncService repositorySyncService;

    @Mock
    private GitHubPushCommitService pushCommitService;

    @Mock
    private GitHubCommitSyncService commitSyncService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private GitHubPushMessageHandler handler;

    private static final long WORKSPACE_ID = 42L;

    @BeforeEach
    void setUp() {
        handler = new GitHubPushMessageHandler(
            repositorySyncService,
            pushCommitService,
            commitSyncService,
            workspaceRepository
        );
    }

    @Test
    void handleEventEnrichesCommitsWhenRepositoryPresent(@GitHubPayload("push") GHEventPayload.Push payload) {
        var repository = payload.getRepository();
        var workspace = workspace();

        when(workspaceRepository.findByRepositoriesToMonitor_NameWithOwner(repository.getFullName())).thenReturn(
            Optional.of(workspace)
        );
        when(repositorySyncService.syncRepository(WORKSPACE_ID, repository.getFullName())).thenReturn(
            Optional.of(repository)
        );

        handler.handleEvent(payload);

        verify(repositorySyncService).processRepository(repository);
        verify(pushCommitService).ingestPush(payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> shasCaptor = ArgumentCaptor.forClass(Set.class);
        verify(commitSyncService).syncCommits(eq(repository), shasCaptor.capture());
        assertThat(shasCaptor.getValue()).containsExactlyInAnyOrderElementsOf(commitShas(payload));
    }

    @Test
    void handleEventSkipsCommitSyncWhenRepositoryMissing() {
        var payload = mock(GHEventPayload.Push.class);
        when(payload.getRepository()).thenReturn(null);

        handler.handleEvent(payload);

        verify(pushCommitService).ingestPush(payload);
        verifyNoInteractions(workspaceRepository);
        verify(commitSyncService, never()).syncCommits(any(), anyCollection());
    }

    @Test
    void handleEventSkipsCommitSyncWhenWorkspaceMissing(@GitHubPayload("push") GHEventPayload.Push payload) {
        when(
            workspaceRepository.findByRepositoriesToMonitor_NameWithOwner(payload.getRepository().getFullName())
        ).thenReturn(Optional.empty());

        handler.handleEvent(payload);

        verify(repositorySyncService).processRepository(payload.getRepository());
        verify(pushCommitService).ingestPush(payload);
        verify(commitSyncService, never()).syncCommits(any(), anyCollection());
    }

    @Test
    void handleEventSkipsCommitSyncWhenHydrationFails(@GitHubPayload("push") GHEventPayload.Push payload) {
        var workspace = workspace();
        when(
            workspaceRepository.findByRepositoriesToMonitor_NameWithOwner(payload.getRepository().getFullName())
        ).thenReturn(Optional.of(workspace));
        when(repositorySyncService.syncRepository(WORKSPACE_ID, payload.getRepository().getFullName())).thenReturn(
            Optional.empty()
        );

        handler.handleEvent(payload);

        verify(commitSyncService, never()).syncCommits(any(), anyCollection());
    }

    private Workspace workspace() {
        var workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        return workspace;
    }

    private Set<String> commitShas(GHEventPayload.Push payload) {
        return payload
            .getCommits()
            .stream()
            .map(GHEventPayload.Push.PushCommit::getSha)
            .filter(sha -> sha != null && !sha.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
