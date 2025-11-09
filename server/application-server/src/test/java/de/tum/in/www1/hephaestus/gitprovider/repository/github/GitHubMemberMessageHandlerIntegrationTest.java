package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaboratorRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("GitHub Member Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubMemberMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubMemberMessageHandler handler;

    @Autowired
    private RepositoryCollaboratorRepository collaboratorRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("creates collaborator entries on member.added")
    void memberAddedPersistsCollaborator(@GitHubPayload("member.added") GHEventPayload.Member payload) {
        handler.handleEvent(payload);

        var repository = repositoryRepository.findById(payload.getRepository().getId()).orElseThrow();
        assertThat(repository.getName()).isEqualTo(payload.getRepository().getName());

        var user = userRepository.findById(payload.getMember().getId()).orElseThrow();
        assertThat(user.getLogin()).isEqualTo(payload.getMember().getLogin());

        var collaborator = collaboratorRepository
            .findByRepositoryIdAndUserId(repository.getId(), user.getId())
            .orElseThrow();
        assertThat(collaborator.getPermission()).isEqualTo(RepositoryCollaborator.Permission.WRITE);
    }

    @Test
    @DisplayName("updates permission on member.edited")
    void memberEditedUpdatesPermission(
        @GitHubPayload("member.added") GHEventPayload.Member added,
        @GitHubPayload("member.edited") GHEventPayload.Member edited
    ) {
        handler.handleEvent(added);
        var collaboratorId = new RepositoryCollaborator.Id(added.getRepository().getId(), added.getMember().getId());
        var before = collaboratorRepository.findById(collaboratorId).orElseThrow();
        assertThat(before.getPermission()).isEqualTo(RepositoryCollaborator.Permission.WRITE);

        handler.handleEvent(edited);

        var updated = collaboratorRepository.findById(collaboratorId).orElseThrow();
        assertThat(updated.getPermission()).isEqualTo(RepositoryCollaborator.Permission.MAINTAIN);
    }

    @Test
    @DisplayName("removes collaborators on member.removed")
    void memberRemovedDeletesCollaborator(
        @GitHubPayload("member.added") GHEventPayload.Member added,
        @GitHubPayload("member.removed") GHEventPayload.Member removed
    ) {
        handler.handleEvent(added);
        var collaboratorId = new RepositoryCollaborator.Id(added.getRepository().getId(), added.getMember().getId());
        assertThat(collaboratorRepository.findById(collaboratorId)).isPresent();

        handler.handleEvent(removed);

        assertThat(collaboratorRepository.findById(collaboratorId)).isEmpty();
    }

    @Test
    @DisplayName("ignores duplicate adds without duplicating rows")
    void memberAddedIsIdempotent(@GitHubPayload("member.added") GHEventPayload.Member payload) {
        handler.handleEvent(payload);
        handler.handleEvent(payload);

        var collaborators = collaboratorRepository.findByRepositoryIdAndUserId(
            payload.getRepository().getId(),
            payload.getMember().getId()
        );
        assertThat(collaborators).isPresent();
        assertThat(collaboratorRepository.count()).isEqualTo(1);
    }
}
