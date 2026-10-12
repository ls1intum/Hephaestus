package de.tum.cit.aet.hephaestus.integration.scm.github.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class GitHubRepositoryIdentityIntegrationTest extends AbstractWorkspaceIntegrationTest {
    private static final String NAME = "shared-org/shared-repo";
    private static final long OLD_ID = 771L;

    @Autowired
    private GitHubRepositoryMessageHandler handler;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void shouldChangeOnlyTheAttestedProvidersRepositoryAcrossArchiveAndVisibilityReverseStates() {
        Repository github = repository(ensureGitHubProvider(), OLD_ID);
        Repository gitlab = repository(ensureGitLabProvider(), OLD_ID);
        deliver(event("archived", OLD_ID));
        deliver(event("privatized", OLD_ID));
        assertThat(repositories.findById(github.getId()).orElseThrow()).satisfies(row -> {
            assertThat(row.isArchived()).isTrue();
            assertThat(row.getVisibility()).isEqualTo(Repository.Visibility.PRIVATE);
        });
        assertThat(repositories.findById(gitlab.getId()).orElseThrow()).satisfies(row -> {
            assertThat(row.isArchived()).isFalse();
            assertThat(row.getVisibility()).isEqualTo(Repository.Visibility.PUBLIC);
        });
        deliver(event("unarchived", OLD_ID));
        deliver(event("publicized", OLD_ID));
        assertThat(repositories.findById(github.getId()).orElseThrow()).satisfies(row -> {
            assertThat(row.isArchived()).isFalse();
            assertThat(row.getVisibility()).isEqualTo(Repository.Visibility.PUBLIC);
        });
    }

    @Test
    void shouldLeaveANameReplacementIntactWhenAnOldRepositoryEventArrives() {
        Repository replacement = repository(ensureGitHubProvider(), OLD_ID + 1);
        deliver(event("archived", OLD_ID));
        deliver(event("privatized", OLD_ID));
        deliver(event("deleted", OLD_ID));
        assertThat(repositories.findById(replacement.getId()).orElseThrow()).satisfies(row -> {
            assertThat(row.getNameWithOwner()).isEqualTo(NAME);
            assertThat(row.isArchived()).isFalse();
            assertThat(row.getVisibility()).isEqualTo(Repository.Visibility.PUBLIC);
        });
        deliver(event("archived", null));
        assertThat(repositories.findById(replacement.getId()).orElseThrow().isArchived())
                .isTrue();
    }

    @Test
    void shouldDeleteOnlyTheExactProviderRepositoryWhenTheInstallationIsNotInThePayload() {
        Repository github = repository(ensureGitHubProvider(), OLD_ID);
        Repository gitlab = repository(ensureGitLabProvider(), OLD_ID);
        deliver(event("deleted", OLD_ID));
        assertThat(repositories.findById(github.getId())).isEmpty();
        assertThat(repositories.findById(gitlab.getId())).isPresent();
    }

    private Repository repository(IdentityProvider provider, long nativeId) {
        Repository repository = new Repository();
        repository.setProvider(provider);
        repository.setNativeId(nativeId);
        repository.setName("shared-repo");
        repository.setNameWithOwner(NAME);
        repository.setHtmlUrl(provider.getServerUrl() + "/" + NAME);
        repository.setVisibility(Repository.Visibility.PUBLIC);
        return repositories.saveAndFlush(repository);
    }

    private static GitHubRepositoryEventDTO event(String action, @Nullable Long nativeId) {
        return new GitHubRepositoryEventDTO(
                action,
                new GitHubRepositoryRefDTO(nativeId, "R_fixture", "shared-repo", NAME, false, null, "main"),
                null,
                null,
                null);
    }

    private void deliver(GitHubRepositoryEventDTO event) {
        transactions.executeWithoutResult(status -> handler.handleEvent(event));
    }
}
