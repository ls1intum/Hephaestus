package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
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
import org.kohsuke.github.GHEventPayloadIssueWithType;
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
    private OrganizationRepository organizationRepository;

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
    void shouldPersistIssueOnOpenedEvent(@GitHubPayload("issues.opened") GHEventPayloadIssueWithType payload)
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
        @GitHubPayload("issues.opened") GHEventPayloadIssueWithType opened,
        @GitHubPayload("issues.edited") GHEventPayloadIssueWithType edited
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
        @GitHubPayload("issues.opened") GHEventPayloadIssueWithType opened,
        @GitHubPayload("issues.closed") GHEventPayloadIssueWithType closed
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
        @GitHubPayload("issues.milestoned") GHEventPayloadIssueWithType milestoned,
        @GitHubPayload("issues.demilestoned") GHEventPayloadIssueWithType demilestoned
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
        @GitHubPayload("issues.locked") GHEventPayloadIssueWithType locked,
        @GitHubPayload("issues.unlocked") GHEventPayloadIssueWithType unlocked
    ) throws Exception {
        handler.handleEvent(locked);
        assertThat(getIssue(locked.getIssue()).isLocked()).isTrue();

        handler.handleEvent(unlocked);
        assertThat(getIssue(unlocked.getIssue()).isLocked()).isFalse();
    }

    @Test
    void shouldRemoveIssueOnDeletedEvent(@GitHubPayload("issues.deleted") GHEventPayloadIssueWithType deleted)
        throws Exception {
        persistPlaceholderIssue(deleted.getIssue());

        handler.handleEvent(deleted);

        assertThat(issueRepository.findById(deleted.getIssue().getId())).isEmpty();
    }

    @Test
    void shouldClearAssigneesOnUnassignedEvent(
        @GitHubPayload("issues.unassigned") GHEventPayloadIssueWithType unassigned
    ) throws Exception {
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
        @GitHubPayload("issues.labeled") GHEventPayloadIssueWithType labeled,
        @GitHubPayload("issues.unlabeled") GHEventPayloadIssueWithType unlabeled
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
        @GitHubPayload("issues.milestoned") GHEventPayloadIssueWithType baseline,
        @GitHubPayload("issues.transferred") GHEventPayloadIssueWithType transferred
    ) throws Exception {
        handler.handleEvent(baseline);
        Issue existing = getIssue(baseline.getIssue());
        long originalRepositoryId = existing.getRepository().getId();

        handler.handleEvent(transferred);

        Issue afterTransfer = getIssue(transferred.getIssue());
        assertThat(afterTransfer.getRepository().getId()).isEqualTo(originalRepositoryId);
    }

    // ========================================
    // Issue Type Tests
    // ========================================

    @Test
    void shouldLinkIssueTypeOnTypedEvent(@GitHubPayload("issues.typed") GHEventPayloadIssueWithType typed)
        throws Exception {
        // First create the organization so the issue type can be created
        persistOrganization(typed.getRepository().getOwner().getLogin(), typed.getRepository().getOwner().getId());

        handler.handleEvent(typed);

        Issue issue = getIssue(typed.getIssue());
        assertThat(issue).isNotNull();
        assertThat(typed.getIssueType()).isNotNull();
        assertThat(typed.getIssueType().getName()).isEqualTo("Task");
        // Issue type should be linked from the handler (if org exists)
        // Note: The actual linking depends on GitHubIssueTypeSyncService behavior
        assertThat(issue.getIssueType()).isNotNull();
        assertThat(issue.getIssueType().getName()).isEqualTo("Task");
        assertThat(issue.getIssueType().getColor()).isEqualTo(
            de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType.Color.YELLOW
        );
    }

    @Test
    void shouldClearIssueTypeOnUntypedEvent(
        @GitHubPayload("issues.typed") GHEventPayloadIssueWithType typed,
        @GitHubPayload("issues.untyped") GHEventPayloadIssueWithType untyped
    ) throws Exception {
        // First create the organization and link the issue type via typed event
        persistOrganization(typed.getRepository().getOwner().getLogin(), typed.getRepository().getOwner().getId());
        handler.handleEvent(typed);

        Issue issueWithType = getIssue(typed.getIssue());
        assertThat(issueWithType.getIssueType()).isNotNull();

        // Now process untyped event - should clear the type
        handler.handleEvent(untyped);

        Issue issueWithoutType = getIssue(untyped.getIssue());
        assertThat(issueWithoutType.getIssueType()).isNull();
    }

    @Test
    void shouldParseIssueTypeFromPayload(@GitHubPayload("issues.typed") GHEventPayloadIssueWithType typed) {
        // Verify the payload parsing works correctly
        assertThat(typed.isTypedAction()).isTrue();
        assertThat(typed.isUntypedAction()).isFalse();
        assertThat(typed.getIssueType()).isNotNull();
        assertThat(typed.getIssueType().getNodeId()).isEqualTo("IT_kwDODNYmp84Bn3q9");
        assertThat(typed.getIssueType().getName()).isEqualTo("Task");
        assertThat(typed.getIssueType().getColor()).isEqualTo("yellow");
        assertThat(typed.getIssueType().getDescription()).isEqualTo("A specific piece of work");
        assertThat(typed.getIssueType().isEnabled()).isTrue();
    }

    @Test
    void shouldParseUntypedPayload(@GitHubPayload("issues.untyped") GHEventPayloadIssueWithType untyped) {
        assertThat(untyped.isTypedAction()).isFalse();
        assertThat(untyped.isUntypedAction()).isTrue();
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void persistOrganization(String login, long id) {
        var org = new Organization();
        org.setId(id);
        org.setGithubId(id);
        org.setLogin(login);
        org.setAvatarUrl("https://example.com/avatar.png");
        organizationRepository.save(org);
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
