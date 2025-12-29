package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubRepositoryCollaboratorSyncServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubRepositoryCollaboratorSyncService collaboratorSyncService;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryCollaboratorRepository collaboratorRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("syncCollaborators persists and prunes collaborators")
    void syncCollaboratorsPersistsAndPrunes() throws Exception {
        Repository repository = new Repository();
        repository.setId(4242L);
        repository.setName("hephaestus");
        repository.setNameWithOwner("ls1intum/hephaestus");
        repository.setHtmlUrl("https://github.com/ls1intum/hephaestus");
        repository.setPushedAt(Instant.now());
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository.setPrivate(false);
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setDefaultBranch("main");
        repository.setHasIssues(true);
        repository.setHasProjects(false);
        repository.setHasWiki(false);
        repositoryRepository.save(repository);

        GHUser ghUser = mock(GHUser.class, Mockito.RETURNS_DEEP_STUBS);
        long userId = 1337L;
        when(ghUser.getId()).thenReturn(userId);
        when(ghUser.getLogin()).thenReturn("octocat");
        when(ghUser.getAvatarUrl()).thenReturn("https://avatars.githubusercontent.com/u/1337?v=4");
        when(ghUser.getBio()).thenReturn("friendly test user");
        when(ghUser.getHtmlUrl()).thenReturn(URI.create("https://github.com/octocat").toURL());
        when(ghUser.getName()).thenReturn("The Octocat");
        when(ghUser.getCompany()).thenReturn("GitHub");
        when(ghUser.getBlog()).thenReturn("https://github.blog");
        when(ghUser.getLocation()).thenReturn("World Wide Web");
        when(ghUser.getEmail()).thenReturn("octocat@example.com");
        when(ghUser.getType()).thenReturn("User");
        when(ghUser.getFollowersCount()).thenReturn(42);
        when(ghUser.getFollowingCount()).thenReturn(12);
        when(ghUser.getCreatedAt()).thenReturn(Instant.now());
        when(ghUser.getUpdatedAt()).thenReturn(Instant.now());

        AtomicReference<List<GHUser>> collaboratorsRef = new AtomicReference<>(List.of(ghUser));
        PagedIterable<GHUser> collaborators = new StubPagedIterable(collaboratorsRef);

        GHRepository ghRepository = mock(GHRepository.class);
        when(ghRepository.getId()).thenReturn(repository.getId());
        when(ghRepository.listCollaborators()).thenReturn(collaborators);
        when(ghRepository.getPermission(ghUser)).thenReturn(GHPermissionType.ADMIN);

        collaboratorSyncService.syncCollaborators(ghRepository);

        var persisted = collaboratorRepository.findByRepositoryIdAndUserId(repository.getId(), userId).orElseThrow();
        assertThat(persisted.getPermission()).isEqualTo(RepositoryCollaborator.Permission.ADMIN);
        assertThat(userRepository.findById(userId)).isPresent();

        collaboratorsRef.set(Collections.emptyList());
        collaboratorSyncService.syncCollaborators(ghRepository);

        assertThat(collaboratorRepository.findByRepositoryIdAndUserId(repository.getId(), userId)).isEmpty();
    }

    private static final class StubPagedIterable extends PagedIterable<GHUser> {

        private final AtomicReference<List<GHUser>> collaborators;

        private StubPagedIterable(AtomicReference<List<GHUser>> collaborators) {
            this.collaborators = collaborators;
        }

        @Override
        public @Nonnull PagedIterator<GHUser> _iterator(int pageSize) {
            List<GHUser> snapshot = collaborators.get();
            List<GHUser[]> pages = snapshot.isEmpty()
                ? Collections.emptyList()
                : Collections.singletonList(snapshot.toArray(GHUser[]::new));
            try {
                Constructor<?> constructor = PagedIterator.class.getDeclaredConstructor(
                    Iterator.class,
                    java.util.function.Consumer.class
                );
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked")
                PagedIterator<GHUser> iterator = (PagedIterator<GHUser>) constructor.newInstance(
                    pages.iterator(),
                    null
                );
                return iterator;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to create paged iterator stub", e);
            }
        }
    }
}
