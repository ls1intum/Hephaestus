package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("GitHub Issue Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubIssueMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubIssueMessageHandler handler;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitHubLabelConverter labelConverter;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReturnCorrectHandlerEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.ISSUES);
    }

    @Test
    void shouldPersistIssueOnOpenedEvent(@GitHubPayload("issues.opened") GHEventPayload.Issue payload)
        throws Exception {
        handler.handleEvent(payload);

        var ghIssue = payload.getIssue();
        Issue issue = getIssue(ghIssue);

        assertThat(issue.getTitle()).isEqualTo(ghIssue.getTitle());
        assertThat(issue.getState()).isEqualTo(Issue.State.OPEN);
        assertThat(issue.getStateReason()).isNull();
        assertThat(issue.getCommentsCount()).isEqualTo(ghIssue.getCommentsCount());
        assertThat(issue.isLocked()).isFalse();
        assertThat(issue.getAuthor()).isNotNull();
        assertThat(issue.getAuthor().getId()).isEqualTo(ghIssue.getUser().getId());
        assertThat(issue.getRepository()).isNotNull();
        assertThat(issue.getRepository().getId()).isEqualTo(payload.getRepository().getId());
        assertThat(labelNames(issue)).containsExactlyInAnyOrderElementsOf(labelNames(ghIssue));
    }

    @Test
    void shouldUpdateBodyAndCommentsOnEditedEvent(
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.edited") GHEventPayload.Issue edited
    ) throws Exception {
        handler.handleEvent(opened);

        Issue existing = getIssue(opened.getIssue());
        existing.setCommentsCount(5);
        issueRepository.save(existing);

        handler.handleEvent(edited);

        Issue updated = getIssue(edited.getIssue());
        assertThat(updated.getBody()).isEqualTo(edited.getIssue().getBody());
        assertThat(updated.getCommentsCount()).isEqualTo(edited.getIssue().getCommentsCount());
    }

    @Test
    void shouldUpdateStateReasonOnClosedEvent(
        @GitHubPayload("issues.opened") GHEventPayload.Issue opened,
        @GitHubPayload("issues.closed") GHEventPayload.Issue closed
    ) throws Exception {
        handler.handleEvent(opened);

        Issue existing = getIssue(opened.getIssue());
        existing.setUpdatedAt(closed.getIssue().getUpdatedAt());
        issueRepository.save(existing);

        handler.handleEvent(closed);

        Issue issue = getIssue(closed.getIssue());
        assertThat(issue.getState()).isEqualTo(Issue.State.CLOSED);
        assertThat(issue.getStateReason()).isEqualTo(Issue.StateReason.COMPLETED);
        assertThat(issue.getClosedAt()).isEqualTo(closed.getIssue().getClosedAt());
    }

    @Test
    void shouldToggleMilestoneOnMilestoneEvents(
        @GitHubPayload("issues.milestoned") GHEventPayload.Issue milestoned,
        @GitHubPayload("issues.demilestoned") GHEventPayload.Issue demilestoned
    ) throws Exception {
        handler.handleEvent(milestoned);
        Issue issue = getIssue(milestoned.getIssue());
        assertThat(issue.getMilestone()).isNotNull();
        assertThat(issue.getMilestone().getId()).isEqualTo(milestoned.getIssue().getMilestone().getId());

        handler.handleEvent(demilestoned);
        Issue withoutMilestone = getIssue(demilestoned.getIssue());
        assertThat(withoutMilestone.getMilestone()).isNull();
    }

    @Test
    void shouldToggleLockStateOnLockEvents(
        @GitHubPayload("issues.locked") GHEventPayload.Issue locked,
        @GitHubPayload("issues.unlocked") GHEventPayload.Issue unlocked
    ) throws Exception {
        handler.handleEvent(locked);
        assertThat(getIssue(locked.getIssue()).isLocked()).isTrue();

        handler.handleEvent(unlocked);
        assertThat(getIssue(unlocked.getIssue()).isLocked()).isFalse();
    }

    @Test
    void shouldRemoveIssueOnDeletedEvent(@GitHubPayload("issues.deleted") GHEventPayload.Issue deleted)
        throws Exception {
        persistPlaceholderIssue(deleted.getIssue());

        handler.handleEvent(deleted);

        assertThat(issueRepository.findById(deleted.getIssue().getId())).isEmpty();
    }

    @Test
    void shouldClearAssigneesOnUnassignedEvent(@GitHubPayload("issues.unassigned") GHEventPayload.Issue unassigned)
        throws Exception {
        Issue issue = persistPlaceholderIssue(unassigned.getIssue());

        User assignee = new User();
        assignee.setId(999L);
        assignee.setLogin("dummy-assignee");
        assignee.setAvatarUrl("https://example.com/avatar.png");
        assignee.setName("Dummy Assignee");
        assignee.setHtmlUrl("https://example.com/profile");
        assignee.setType(User.Type.USER);
        userRepository.save(assignee);

        issue.getAssignees().add(assignee);
        issueRepository.save(issue);

        handler.handleEvent(unassigned);

        assertThat(getIssue(unassigned.getIssue()).getAssignees()).isEmpty();
    }

    @Test
    void shouldDropLabelsOnUnlabeledEvent(
        @GitHubPayload("issues.labeled") GHEventPayload.Issue labeled,
        @GitHubPayload("issues.unlabeled") GHEventPayload.Issue unlabeled
    ) throws Exception {
        handler.handleEvent(labeled);
        Issue issue = getIssue(labeled.getIssue());

        var bugLabel = labelConverter.convert(unlabeled.getLabel());
        bugLabel.setRepository(issue.getRepository());
        labelRepository.save(bugLabel);

        issue.getLabels().add(bugLabel);
        issueRepository.save(issue);

        handler.handleEvent(unlabeled);

        Issue updated = getIssue(unlabeled.getIssue());
        assertThat(labelNames(updated)).containsExactlyInAnyOrderElementsOf(labelNames(unlabeled.getIssue()));
    }

    @Test
    void shouldKeepExistingRepositoryOnTransferredEvent(
        @GitHubPayload("issues.milestoned") GHEventPayload.Issue baseline,
        @GitHubPayload("issues.transferred") GHEventPayload.Issue transferred
    ) throws Exception {
        handler.handleEvent(baseline);
        Issue existing = getIssue(baseline.getIssue());
        long originalRepositoryId = existing.getRepository().getId();

        handler.handleEvent(transferred);

        Issue afterTransfer = getIssue(transferred.getIssue());
        assertThat(afterTransfer.getRepository().getId()).isEqualTo(originalRepositoryId);
    }

    private Issue getIssue(GHIssue ghIssue) {
        return issueRepository
            .findById(ghIssue.getId())
            .orElseThrow(() -> new AssertionError("Issue " + ghIssue.getId() + " was not persisted"));
    }

    private Issue persistPlaceholderIssue(GHIssue ghIssue) {
        Issue issue = new Issue();
        issue.setId(ghIssue.getId());
        issue.setNumber(ghIssue.getNumber());
        issue.setState(Issue.State.OPEN);
        issue.setTitle(ghIssue.getTitle() != null ? ghIssue.getTitle() : "placeholder");
        issue.setBody(ghIssue.getBody());
        issue.setHtmlUrl(ghIssue.getHtmlUrl().toString());
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        return issueRepository.save(issue);
    }

    private Set<String> labelNames(Issue issue) {
        return issue.getLabels().stream().map(Label::getName).collect(Collectors.toSet());
    }

    private Set<String> labelNames(GHIssue ghIssue) {
        return ghIssue.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
    }
}
