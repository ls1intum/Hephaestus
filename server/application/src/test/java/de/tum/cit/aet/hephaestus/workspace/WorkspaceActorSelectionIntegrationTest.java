package de.tum.cit.aet.hephaestus.workspace;

import static de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome.NOT_MET;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery.MembershipView;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.AbstractPracticeReviewIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.PracticePageFeedbackBody;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * An account whose first-linked actor owns a workspace connected to another provider or another
 * instance of the same provider, where its later-linked actor is the one reviewed. The account
 * reads the workspace as its actor on the connected instance, and keeps what it started through
 * either actor.
 */
class WorkspaceActorSelectionIntegrationTest extends AbstractPracticeReviewIntegrationTest {
    @Autowired
    private PracticeModelTestFixtures modelFixtures;

    @Autowired
    private IdentityRepository identities;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private AccountMembershipQuery memberships;

    @Autowired
    private ChatThreadRepository threadRepository;

    @Test
    void shouldReadAWorkspaceAsTheActorOnItsActiveProviderWhenAnotherProviderWasLinkedFirst() {
        User gitlabActor = userRepository.saveAndFlush(
                TestUserFactory.createUser(264_583_885L, "felix-gitlab", ensureGitLabProvider()));
        User githubActor = persistUser("felix-github");
        Account account = persistAccount("Two providers");
        link(account, gitlabActor);
        link(account, githubActor);

        Workspace workspace = createWorkspace("actor-github", "GitHub", "acme", AccountType.ORG, gitlabActor);
        ensureMembership(workspace, githubActor, Membership.WorkspaceRole.MEMBER);
        connect(workspace, IntegrationKind.GITHUB, new ConnectionConfig.GitHubPatConfig("acme", null, Set.of()));
        Practice practice = persistPractice(workspace, null, "github-practice", "GitHub practice", null);
        ReviewJob run = persistPullRequestReview(workspace, 1, NOW.minus(Duration.ofDays(1)));
        observe(practice, run, 1L, githubActor, NOT_MET, Severity.MAJOR, NOW.minus(Duration.ofDays(1)));

        String token = "mock-jwt-user-sub-" + account.getId();
        read(token, "/workspaces/actor-github/members/me")
                .jsonPath("$.userId")
                .isEqualTo(githubActor.getId())
                .jsonPath("$.role")
                .isEqualTo("OWNER");
        read(token, "/workspaces/actor-github/practices/standings")
                .jsonPath("$[0].slug")
                .isEqualTo("github-practice")
                // One problem on one piece of work: a verdict read for this actor, one setback.
                .jsonPath("$[0].standing")
                .isEqualTo("MIXED");
        read(token, "/workspaces/actor-github/practice-profile/overview")
                .jsonPath("$.latestRun.reviewId")
                .isEqualTo(run.getId().toString());

        // Slack and Outline attribute through the same account view.
        assertThat(memberships.membershipsForAccount(Objects.requireNonNull(account.getId())))
                .extracting(MembershipView::memberId)
                .containsExactly(githubActor.getId());
    }

    @Test
    void shouldKeepTheAccountsConversationsWhenTheWorkspaceReadsItAsAnotherActor() {
        User gitlabActor = userRepository.saveAndFlush(
                TestUserFactory.createUser(264_583_886L, "felix-gitlab-threads", ensureGitLabProvider()));
        User githubActor = persistUser("felix-github-threads");
        User colleague = persistUser("colleague-threads");
        Account account = persistAccount("Two providers with conversations");
        link(account, gitlabActor);
        link(account, githubActor);

        Workspace workspace = createWorkspace("actor-threads", "Threads", "acme", AccountType.ORG, gitlabActor);
        ensureMembership(workspace, githubActor, Membership.WorkspaceRole.MEMBER);
        ensureMembership(workspace, colleague, Membership.WorkspaceRole.MEMBER);
        connect(workspace, IntegrationKind.GITHUB, new ConnectionConfig.GitHubPatConfig("acme", null, Set.of()));
        UUID earlier = persistThread(workspace, gitlabActor, "Started as the GitLab actor");
        UUID current = persistThread(workspace, githubActor, "Started as the GitHub actor");
        UUID colleagues = persistThread(workspace, colleague, "A colleague's conversation");
        Workspace elsewhere = createWorkspace("actor-threads-other", "Other", "other", AccountType.ORG, githubActor);
        UUID otherWorkspace = persistThread(elsewhere, githubActor, "Another workspace");

        String token = "mock-jwt-member-" + account.getId();
        String threads = "/workspaces/actor-threads/mentor/threads";
        read(token, threads)
                .jsonPath("$[*].id")
                .value(ids -> assertThat(ids)
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .contains(earlier.toString(), current.toString())
                        .doesNotContain(colleagues.toString(), otherWorkspace.toString()));
        read(token, threads + "/" + earlier).jsonPath("$.id").isEqualTo(earlier.toString());
        webTestClient
                .get()
                .uri(threads + "/" + colleagues)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
        webTestClient
                .get()
                .uri(threads + "/" + otherWorkspace)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    void shouldReadAWorkspaceAsTheActorOnItsConnectedInstanceWhenAnotherInstanceOfTheProviderWasLinkedFirst() {
        User saasActor =
                userRepository.saveAndFlush(TestUserFactory.createUser(4_001L, "felix-saas", ensureGitLabProvider()));
        User selfHostedActor = userRepository.saveAndFlush(TestUserFactory.createUser(
                4_002L,
                "ga84xah",
                gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITLAB, "https://gitlab.lrz.de"))));
        Account account = persistAccount("Two GitLab instances");
        link(account, saasActor);
        link(account, selfHostedActor);

        Workspace workspace = createWorkspace("actor-lrz", "LRZ", "group", AccountType.ORG, saasActor);
        ensureMembership(workspace, selfHostedActor, Membership.WorkspaceRole.MEMBER);
        connect(
                workspace,
                IntegrationKind.GITLAB,
                new ConnectionConfig.GitLabConfig(
                        "https://gitlab.lrz.de",
                        null,
                        null,
                        ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                        Set.of(),
                        null));
        Practice practice = persistPractice(workspace, null, "lrz-practice", "LRZ practice", null);
        ReviewJob run = persistPullRequestReview(workspace, 3, NOW.minus(Duration.ofDays(1)));
        UUID practiceResult =
                observe(practice, run, 3L, selfHostedActor, NOT_MET, Severity.MAJOR, NOW.minus(Duration.ofDays(1)));
        Feedback feedback = persistPracticePageFeedback(
                run,
                selfHostedActor,
                1,
                FeedbackState.DELIVERED,
                PracticePageFeedbackBody.render("A way of working", "What recurs.", "One thing to try."),
                NOW.minus(Duration.ofDays(1)));
        bind(feedback, practiceResult);

        String token = "mock-jwt-user-sub-" + account.getId();
        read(token, "/workspaces/actor-lrz/members/me")
                .jsonPath("$.userId")
                .isEqualTo(selfHostedActor.getId())
                .jsonPath("$.role")
                .isEqualTo("OWNER");
        read(token, "/workspaces/actor-lrz/practices/standings")
                .jsonPath("$[0].slug")
                .isEqualTo("lrz-practice")
                // One problem on one piece of work: a verdict read for this actor, one setback.
                .jsonPath("$[0].standing")
                .isEqualTo("MIXED");
        read(token, "/workspaces/actor-lrz/practices/feedback/in-app")
                .jsonPath("$[0].id")
                .isEqualTo(feedback.getId().toString());
        assertThat(memberships.membershipsForAccount(Objects.requireNonNull(account.getId())))
                .extracting(MembershipView::memberId)
                .containsExactly(selfHostedActor.getId());
    }

    private WebTestClient.BodyContentSpec read(String token, String uri) {
        return webTestClient
                .get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
    }

    private void link(Account account, User actor) {
        Identity link = new Identity();
        link.setAccount(account);
        link.setProviderId(Objects.requireNonNull(actor.getProvider().getId()));
        link.setProviderUserKey(actor.getNativeId().toString());
        identities.saveAndFlush(link);
    }

    private UUID persistThread(Workspace workspace, User owner, String title) {
        ChatThread thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setWorkspace(workspace);
        thread.setAccountId(accountId(owner));
        thread.setTitle(title);
        return threadRepository.saveAndFlush(thread).getId();
    }

    private void connect(Workspace workspace, IntegrationKind kind, ConnectionConfig config) {
        Connection connection = new Connection(workspace, kind, kind.name(), config);
        connection.setState(IntegrationState.ACTIVE);
        connections.saveAndFlush(connection);
    }
}
