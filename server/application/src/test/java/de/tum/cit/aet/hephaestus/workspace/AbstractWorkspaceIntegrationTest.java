package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.lifecycle.GitHubLifecycleListener;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractWorkspaceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected IdentityProviderRepository gitProviderRepository;

    @Autowired
    protected WorkspaceService workspaceService;

    @Autowired
    protected GitHubLifecycleListener githubLifecycleListener;

    @Autowired
    protected WorkspaceMembershipService workspaceMembershipService;

    @Autowired
    protected WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    private IdentityLinkRepository fixtureIdentities;

    @Autowired
    private ConnectionRepository activityConnections;

    @Autowired
    private ConnectionService activityConnectionService;

    private final AtomicLong userIdGenerator = new AtomicLong(50_000);

    @BeforeEach
    void resetWorkspaceDatabaseState() {
        databaseTestUtils.cleanDatabase();
    }

    /** Register the source that makes an activity fixture's monitored repositories authoritative. */
    protected void connectActivitySource(Workspace workspace, IdentityProvider provider) {
        ConnectionConfig config =
                switch (provider.getType()) {
                    case GITHUB ->
                        new ConnectionConfig.GitHubAppConfig(
                                workspace.getId(), workspace.getAccountLogin(), provider.getServerUrl(), Set.of());
                    case GITLAB ->
                        new ConnectionConfig.GitLabConfig(
                                provider.getServerUrl(),
                                null,
                                null,
                                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                                Set.of(),
                                null);
                    default -> throw new IllegalArgumentException("Activity source must be GitHub or GitLab");
                };
        IntegrationKind kind =
                provider.getType() == IdentityProviderType.GITLAB ? IntegrationKind.GITLAB : IntegrationKind.GITHUB;
        Connection connection =
                activityConnections.saveAndFlush(new Connection(workspace, kind, "activity-fixture", config));
        activityConnectionService.transition(
                connection,
                new ConnectionService.TransitionRequest(
                        IntegrationState.ACTIVE,
                        "TEST_CONNECTED",
                        "SYSTEM",
                        "activity-fixture",
                        UUID.randomUUID().toString(),
                        "Source connected"));
    }

    protected IdentityProvider ensureGitHubProvider() {
        return gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
    }

    protected IdentityProvider ensureGitLabProvider() {
        return gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITLAB, "https://gitlab.com")
                .orElseGet(() -> gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITLAB, "https://gitlab.com")));
    }

    protected Account persistAccount(String displayName) {
        return accountRepository.save(new Account(displayName));
    }

    protected Account persistInstanceAdmin(String displayName) {
        Account account = new Account(displayName);
        account.setAppRole(Account.AppRole.APP_ADMIN);
        return accountRepository.save(account);
    }

    protected User persistUser(String login) {
        IdentityProvider provider = ensureGitHubProvider();
        User user = new User();
        long nativeId = userIdGenerator.incrementAndGet();
        user.setNativeId(nativeId);
        user.setProvider(provider);
        user.setLogin(login);
        user.setName("User " + login);
        user.setAvatarUrl("https://example.com/" + login + ".png");
        user.setHtmlUrl("https://github.com/" + login);
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);
        if (Set.of("admin", "mentor", "testuser").contains(login)) {
            TestUserFactory.ensureAccountForUser(accountRepository, fixtureIdentities, user);
        }
        return user;
    }

    protected Workspace createWorkspace(
            String slug, String displayName, String accountLogin, AccountType accountType, User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("Owner user must be persisted before creating a workspace");
        }
        return workspaceService.createWorkspace(slug, displayName, accountLogin, accountType, owner.getId());
    }

    protected WorkspaceMembership ensureWorkspaceMembership(
            Workspace workspace, User user, WorkspaceMembership.WorkspaceRole role) {
        return workspaceMembershipRepository
                .findByWorkspace_IdAndUser_Id(workspace.getId(), user.getId())
                .orElseGet(() -> workspaceMembershipService.createMembership(workspace, user.getId(), role));
    }

    protected WorkspaceMembership ensureAdminMembership(Workspace workspace) {
        User adminUser = TestUserFactory.ensureUser(userRepository, "admin", 3L, ensureGitHubProvider());
        TestUserFactory.ensureAccountForUser(accountRepository, fixtureIdentities, adminUser);
        return ensureWorkspaceMembership(workspace, adminUser, WorkspaceMembership.WorkspaceRole.ADMIN);
    }

    protected WorkspaceMembership ensureOwnerMembership(Workspace workspace) {
        User adminUser = TestUserFactory.ensureUser(userRepository, "admin", 3L, ensureGitHubProvider());
        TestUserFactory.ensureAccountForUser(accountRepository, fixtureIdentities, adminUser);
        return ensureWorkspaceMembership(workspace, adminUser, WorkspaceMembership.WorkspaceRole.OWNER);
    }
}
