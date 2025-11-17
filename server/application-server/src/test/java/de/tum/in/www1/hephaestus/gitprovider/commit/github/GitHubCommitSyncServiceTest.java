package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubCommitSyncServiceTest {

    @Mock
    private GitCommitRepository commitRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitHubUserConverter userConverter;

    @Mock
    private GHRepository ghRepository;

    @Mock
    private Repository repository;

    private GitHubCommitSyncService service;

    @BeforeEach
    void setUp() {
        service = spy(new GitHubCommitSyncService(commitRepository, repositoryRepository, userRepository, userConverter));
    }

    @Test
    void syncCommitsEnrichesOnlyIncompleteShas() {
        when(ghRepository.getId()).thenReturn(99L);
        when(repositoryRepository.findById(99L)).thenReturn(Optional.of(repository));
        when(repository.getId()).thenReturn(99L);

        var requested = List.of("sha-a", "sha-b");
        when(commitRepository.findIncompleteCommitShas(99L, requested)).thenReturn(List.of("sha-b"));
        doNothing().when(service).enrichCommit(ghRepository, "sha-b");

        service.syncCommits(ghRepository, requested);

        verify(service).enrichCommit(ghRepository, "sha-b");
        verify(service, never()).enrichCommit(ghRepository, "sha-a");
    }

    @Test
    void syncCommitsSkipsWhenNoIncomplete() {
        when(ghRepository.getId()).thenReturn(7L);
        when(repositoryRepository.findById(7L)).thenReturn(Optional.of(repository));
        when(repository.getId()).thenReturn(7L);
        when(repository.getNameWithOwner()).thenReturn("ls1intum/hephaestus");

        var requested = List.of("sha-a");
        when(commitRepository.findIncompleteCommitShas(7L, requested)).thenReturn(List.of());

        service.syncCommits(ghRepository, requested);

        verify(service, never()).enrichCommit(ghRepository, "sha-a");
    }
}
