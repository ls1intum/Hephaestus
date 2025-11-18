package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadMilestone;
import org.kohsuke.github.GHMilestone;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Milestone Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubMilestoneMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubMilestoneMessageHandler handler;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReturnCorrectHandlerEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.MILESTONE);
    }

    @Test
    @DisplayName("should persist milestones on creation events")
    void createdEventPersistsMilestone(@GitHubPayload("milestone.created") GHEventPayloadMilestone payload)
        throws Exception {
        handler.handleEvent(payload);

        var saved = getMilestone(payload);
        GHMilestone ghMilestone = payload.getMilestone();

        assertThat(saved.getState()).isEqualTo(Milestone.State.OPEN);
        assertThat(saved.getHtmlUrl()).isEqualTo(ghMilestone.getHtmlUrl().toString());
        assertThat(saved.getTitle()).isEqualTo(ghMilestone.getTitle());
        assertThat(saved.getDescription()).isEqualTo(ghMilestone.getDescription());
        assertThat(saved.getDueOn()).isEqualTo(ghMilestone.getDueOn());
        assertThat(saved.getOpenIssuesCount()).isEqualTo(ghMilestone.getOpenIssues());
        assertThat(saved.getClosedIssuesCount()).isEqualTo(ghMilestone.getClosedIssues());
        assertThat(saved.getCreatedAt()).isEqualTo(ghMilestone.getCreatedAt());
        assertThat(saved.getUpdatedAt()).isEqualTo(ghMilestone.getUpdatedAt());
        assertThat(saved.getCreator()).isNotNull();
        assertThat(saved.getCreator().getId()).isEqualTo(ghMilestone.getCreator().getId());
        assertThat(saved.getRepository()).isNotNull();
        assertThat(saved.getRepository().getId()).isEqualTo(payload.getRepository().getId());
        assertThat(milestoneRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should update milestone details on edit events")
    void editedEventUpdatesMilestone(
        @GitHubPayload("milestone.created") GHEventPayloadMilestone created,
        @GitHubPayload("milestone.edited") GHEventPayloadMilestone edited
    ) throws Exception {
        handler.handleEvent(created);

        handler.handleEvent(edited);

        var updated = getMilestone(edited);
        GHMilestone ghMilestone = edited.getMilestone();

        assertThat(updated.getDescription()).isEqualTo(ghMilestone.getDescription());
        assertThat(updated.getDueOn()).isEqualTo(ghMilestone.getDueOn());
        assertThat(updated.getUpdatedAt()).isEqualTo(ghMilestone.getUpdatedAt());
        assertThat(updated.getOpenIssuesCount()).isEqualTo(ghMilestone.getOpenIssues());
        assertThat(updated.getClosedIssuesCount()).isEqualTo(ghMilestone.getClosedIssues());
    }

    @Test
    @DisplayName("should mark milestones as closed on close events")
    void closedEventMarksMilestone(
        @GitHubPayload("milestone.created") GHEventPayloadMilestone created,
        @GitHubPayload("milestone.closed") GHEventPayloadMilestone closed
    ) throws Exception {
        handler.handleEvent(created);

        handler.handleEvent(closed);

        var saved = getMilestone(closed);
        GHMilestone ghMilestone = closed.getMilestone();

        assertThat(saved.getState()).isEqualTo(Milestone.State.CLOSED);
        assertThat(saved.getClosedAt()).isEqualTo(ghMilestone.getClosedAt());
        assertThat(saved.getDueOn()).isEqualTo(ghMilestone.getDueOn());
        assertThat(saved.getClosedIssuesCount()).isEqualTo(ghMilestone.getClosedIssues());
    }

    @Test
    @DisplayName("should reopen milestones on opened events")
    void openedEventReopensMilestone(
        @GitHubPayload("milestone.created") GHEventPayloadMilestone created,
        @GitHubPayload("milestone.closed") GHEventPayloadMilestone closed,
        @GitHubPayload("milestone.opened") GHEventPayloadMilestone opened
    ) throws Exception {
        handler.handleEvent(created);
        handler.handleEvent(closed);

        handler.handleEvent(opened);

        var reopened = getMilestone(opened);
        GHMilestone ghMilestone = opened.getMilestone();

        assertThat(reopened.getState()).isEqualTo(Milestone.State.OPEN);
        assertThat(reopened.getClosedAt()).isNull();
        assertThat(reopened.getUpdatedAt()).isEqualTo(ghMilestone.getUpdatedAt());
        assertThat(reopened.getOpenIssuesCount()).isEqualTo(ghMilestone.getOpenIssues());
    }

    @Test
    @DisplayName("should delete milestones on delete events")
    void deletedEventRemovesMilestone(
        @GitHubPayload("milestone.created") GHEventPayloadMilestone created,
        @GitHubPayload("milestone.deleted") GHEventPayloadMilestone deleted
    ) throws Exception {
        handler.handleEvent(created);
        assertThat(milestoneRepository.findById(created.getMilestone().getId())).isPresent();

        handler.handleEvent(deleted);

        assertThat(milestoneRepository.findById(deleted.getMilestone().getId())).isEmpty();
    }

    @Test
    @DisplayName("should be idempotent when replaying creation events")
    void createdEventIsIdempotent(@GitHubPayload("milestone.created") GHEventPayloadMilestone payload)
        throws Exception {
        handler.handleEvent(payload);
        var first = getMilestone(payload);
        var initialUsers = userRepository.count();

        handler.handleEvent(payload);

        assertThat(milestoneRepository.count()).isEqualTo(1);
        assertThat(userRepository.count()).isEqualTo(initialUsers);

        var second = getMilestone(payload);
        assertThat(second.getUpdatedAt()).isEqualTo(first.getUpdatedAt());
        assertThat(second.getDescription()).isEqualTo(first.getDescription());
    }

    private Milestone getMilestone(GHEventPayloadMilestone payload) {
        long milestoneId = payload.getMilestone().getId();
        return milestoneRepository
            .findById(milestoneId)
            .orElseThrow(() -> new AssertionError("Milestone " + milestoneId + " was not persisted"));
    }
}
