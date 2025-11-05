package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("GitHub Pull Request Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubPullRequestMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestMessageHandler handler;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private LabelRepository labelRepository;

    @MockitoBean
    private BadPracticeDetectorScheduler badPracticeDetectorScheduler;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
        reset(badPracticeDetectorScheduler);
    }

    @Test
    void shouldReturnCorrectHandlerEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.PULL_REQUEST);
    }

    @Test
    void shouldPersistPullRequestOnOpenedEvent(
        @GitHubPayload("pull_request.opened") GHEventPayload.PullRequest payload
    ) throws Exception {
        handler.handleEvent(payload);

        PullRequest pullRequest = getPullRequest(payload);
        assertThat(pullRequest.getTitle()).isEqualTo(payload.getPullRequest().getTitle());
        assertThat(pullRequest.getState()).isEqualTo(PullRequest.State.OPEN);
        assertThat(pullRequest.isDraft()).isFalse();
        assertThat(pullRequest.getMergeCommitSha()).isNull();
        assertThat(pullRequest.getAuthor().getId()).isEqualTo(payload.getPullRequest().getUser().getId());
        assertThat(pullRequest.getRepository().getId()).isEqualTo(payload.getRepository().getId());
        assertThat(pullRequest.getLabels()).isEmpty();
        ArgumentCaptor<PullRequest> prCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(badPracticeDetectorScheduler).detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(prCaptor.capture());
        assertThat(prCaptor.getValue().getId()).isEqualTo(pullRequest.getId());
    }

    @Test
    void shouldUpdateDraftStatusOnTransitions(
        @GitHubPayload("pull_request.converted_to_draft") GHEventPayload.PullRequest convertedToDraft,
        @GitHubPayload("pull_request.ready_for_review") GHEventPayload.PullRequest readyForReview
    ) throws Exception {
        handler.handleEvent(convertedToDraft);
        PullRequest draft = getPullRequest(convertedToDraft);
        assertThat(draft.isDraft()).isTrue();
        verify(badPracticeDetectorScheduler, never()).detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(any());
        verify(badPracticeDetectorScheduler, never()).detectBadPracticeForPrIfClosedEvent(any());

        reset(badPracticeDetectorScheduler);
        handler.handleEvent(readyForReview);
        PullRequest ready = getPullRequest(readyForReview);
        assertThat(ready.isDraft()).isFalse();
        ArgumentCaptor<PullRequest> readyCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(badPracticeDetectorScheduler).detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(
            readyCaptor.capture()
        );
        assertThat(readyCaptor.getValue().getId()).isEqualTo(ready.getId());
    }

    @Test
    void shouldRefreshAssigneesOnAssignmentEvents(
        @GitHubPayload("pull_request.assigned") GHEventPayload.PullRequest assigned,
        @GitHubPayload("pull_request.unassigned") GHEventPayload.PullRequest unassigned
    ) throws Exception {
        handler.handleEvent(assigned);
        PullRequest withAssignee = getPullRequest(assigned);
        assertThat(userLogins(withAssignee.getAssignees())).containsExactlyInAnyOrder(
            assigned.getPullRequest().getAssignees().stream().map(user -> user.getLogin()).toArray(String[]::new)
        );

        handler.handleEvent(unassigned);
        PullRequest withoutAssignee = getPullRequest(unassigned);
        assertThat(withoutAssignee.getAssignees()).isEmpty();
    }

    @Test
    void shouldRefreshLabelsOnLabelEvents(
        @GitHubPayload("pull_request.labeled") GHEventPayload.PullRequest labeled,
        @GitHubPayload("pull_request.unlabeled") GHEventPayload.PullRequest unlabeled
    ) throws Exception {
        assertThat(labeled.getLabel()).as("GitHub webhook includes the added label").isNotNull();
        assertThat(labeled.getLabel()).isInstanceOf(GHLabel.class);

        handler.handleEvent(labeled);
        PullRequest pullRequest = getPullRequest(labeled);
        assertThat(labelNames(pullRequest)).containsExactly("fixtures");
        assertThat(labelRepository.findById(labeled.getLabel().getId())).isPresent();

        reset(badPracticeDetectorScheduler);
        handler.handleEvent(unlabeled);

        PullRequest updated = getPullRequest(unlabeled);
        assertThat(labelNames(updated)).containsExactlyInAnyOrderElementsOf(labelNames(unlabeled.getPullRequest()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Label>> previousLabelsCaptor = ArgumentCaptor.forClass(
            (Class<Set<Label>>) (Class<?>) Set.class
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Label>> newLabelsCaptor = ArgumentCaptor.forClass((Class<Set<Label>>) (Class<?>) Set.class);
        ArgumentCaptor<PullRequest> prArgumentCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(badPracticeDetectorScheduler).detectBadPracticeForPrIfReadyLabels(
            prArgumentCaptor.capture(),
            previousLabelsCaptor.capture(),
            newLabelsCaptor.capture()
        );

        assertThat(prArgumentCaptor.getValue().getId()).isEqualTo(pullRequest.getId());

        assertThat(previousLabelsCaptor.getValue()).extracting(Label::getName).containsExactly("fixtures");
        assertThat(newLabelsCaptor.getValue())
            .extracting(Label::getName)
            .containsExactlyInAnyOrderElementsOf(labelNames(unlabeled.getPullRequest()));
    }

    @Test
    void shouldUpdateMilestoneOnMilestoneEvents(
        @GitHubPayload("pull_request.milestoned") GHEventPayload.PullRequest milestoned,
        @GitHubPayload("pull_request.demilestoned") GHEventPayload.PullRequest demilestoned
    ) throws Exception {
        handler.handleEvent(milestoned);
        PullRequest pullRequest = getPullRequest(milestoned);
        assertThat(pullRequest.getMilestone()).isNotNull();
        assertThat(pullRequest.getMilestone().getId()).isEqualTo(milestoned.getPullRequest().getMilestone().getId());

        handler.handleEvent(demilestoned);
        PullRequest withoutMilestone = getPullRequest(demilestoned);
        assertThat(withoutMilestone.getMilestone()).isNull();
    }

    @Test
    void shouldUpdateMergeMetadataOnUnlabeledEvent(
        @GitHubPayload("pull_request.assigned") GHEventPayload.PullRequest seed,
        @GitHubPayload("pull_request.unlabeled") GHEventPayload.PullRequest unlabeled
    ) throws Exception {
        handler.handleEvent(seed);
        PullRequest pullRequest = getPullRequest(seed);
        pullRequest.setMergeCommitSha("old-sha");
        pullRequest.setCommits(5);
        pullRequest.setAdditions(5);
        pullRequest.setDeletions(5);
        pullRequest.setChangedFiles(5);
        pullRequestRepository.save(pullRequest);

        handler.handleEvent(unlabeled);

        PullRequest updated = getPullRequest(unlabeled);
        assertThat(updated.getMergeCommitSha()).isEqualTo(unlabeled.getPullRequest().getMergeCommitSha());
        assertThat(updated.getCommits()).isEqualTo(unlabeled.getPullRequest().getCommits());
        assertThat(updated.getAdditions()).isEqualTo(
            unlabeled.getPullRequest().getAdditions() == 0 ? 5 : unlabeled.getPullRequest().getAdditions()
        );
        assertThat(updated.getDeletions()).isEqualTo(
            unlabeled.getPullRequest().getDeletions() == 0 ? 5 : unlabeled.getPullRequest().getDeletions()
        );
        assertThat(updated.getChangedFiles()).isEqualTo(
            unlabeled.getPullRequest().getChangedFiles() == 0 ? 5 : unlabeled.getPullRequest().getChangedFiles()
        );
    }

    @Test
    void shouldInvokeClosedSchedulerOnClosedEvent(
        @GitHubPayload("pull_request.ready_for_review") GHEventPayload.PullRequest ready,
        @GitHubPayload("pull_request.closed") GHEventPayload.PullRequest closed
    ) throws Exception {
        handler.handleEvent(ready);
        reset(badPracticeDetectorScheduler);

        handler.handleEvent(closed);

        PullRequest pullRequest = getPullRequest(closed);
        assertThat(pullRequest.getState()).isEqualTo(PullRequest.State.CLOSED);
        assertThat(pullRequest.getClosedAt()).isEqualTo(closed.getPullRequest().getClosedAt());
        ArgumentCaptor<PullRequest> closedCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(badPracticeDetectorScheduler).detectBadPracticeForPrIfClosedEvent(closedCaptor.capture());
        PullRequest captured = closedCaptor.getValue();
        assertThat(captured.getId()).isEqualTo(pullRequest.getId());
    }

    private PullRequest getPullRequest(GHEventPayload.PullRequest payload) {
        return pullRequestRepository
            .findById(payload.getPullRequest().getId())
            .orElseThrow(() -> new AssertionError("Pull request " + payload.getPullRequest().getId() + " missing"));
    }

    private Set<String> labelNames(PullRequest pullRequest) {
        return pullRequest.getLabels().stream().map(Label::getName).collect(Collectors.toSet());
    }

    private Set<String> labelNames(org.kohsuke.github.GHPullRequest ghPullRequest) {
        return ghPullRequest.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
    }

    private Set<String> userLogins(Set<User> users) {
        return users.stream().map(User::getLogin).collect(Collectors.toSet());
    }
}
