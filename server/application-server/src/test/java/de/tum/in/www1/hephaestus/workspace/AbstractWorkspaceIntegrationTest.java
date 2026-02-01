package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared helpers for workspace-focused integration tests.
 */
public abstract class AbstractWorkspaceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected WorkspaceService workspaceService;

    @Autowired
    protected WorkspaceInstallationService workspaceInstallationService;

    @Autowired
    protected WorkspaceMembershipService workspaceMembershipService;

    @Autowired
    protected WorkspaceMembershipRepository workspaceMembershipRepository;

    private final AtomicLong userIdGenerator = new AtomicLong(50_000);

    @BeforeEach
    void resetWorkspaceDatabaseState() {
        databaseTestUtils.cleanDatabase();
    }

    protected User persistUser(String login) {
        User user = new User();
        user.setId(userIdGenerator.incrementAndGet());
        user.setLogin(login);
        user.setName("User " + login);
        user.setAvatarUrl("https://example.com/" + login + ".png");
        user.setHtmlUrl("https://github.com/" + login);
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    protected Workspace createWorkspace(
        String slug,
        String displayName,
        String accountLogin,
        AccountType accountType,
        User owner
    ) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("Owner user must be persisted before creating a workspace");
        }
        return workspaceService.createWorkspace(slug, displayName, accountLogin, accountType, owner.getId());
    }

    protected WorkspaceMembership ensureWorkspaceMembership(
        Workspace workspace,
        User user,
        WorkspaceMembership.WorkspaceRole role
    ) {
        return workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.getId(), user.getId())
            .orElseGet(() -> workspaceMembershipService.createMembership(workspace, user.getId(), role));
    }

    protected WorkspaceMembership ensureAdminMembership(Workspace workspace) {
        User adminUser = TestUserFactory.ensureUser(userRepository, "admin", 3L);
        return ensureWorkspaceMembership(workspace, adminUser, WorkspaceMembership.WorkspaceRole.ADMIN);
    }

    protected WorkspaceMembership ensureOwnerMembership(Workspace workspace) {
        User adminUser = TestUserFactory.ensureUser(userRepository, "admin", 3L);
        return ensureWorkspaceMembership(workspace, adminUser, WorkspaceMembership.WorkspaceRole.OWNER);
    }
}
