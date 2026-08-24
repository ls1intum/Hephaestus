package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewSubject;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewPersonTargetRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewRepositoryTargetRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewPersonMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryTarget;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticeReviewCoverageServiceTest extends BaseUnitTest {

    @Mock
    private RepositoryToMonitorRepository monitors;

    @Mock
    private WorkspaceMembershipRepository memberships;

    @Mock
    private PracticeReviewRepositoryTargetRepository repositoryTargets;

    @Mock
    private PracticeReviewPersonTargetRepository people;

    private PracticeReviewCoverageService service;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        service = new PracticeReviewCoverageService(monitors, memberships, repositoryTargets, people);
        workspace = new Workspace();
        workspace.setId(1L);
    }

    @Test
    void selectedRepositoryFromAnotherTenantIsRejected() {
        when(monitors.findByWorkspaceId(1L)).thenReturn(List.of(monitor(11L, "owner/local")));
        when(memberships.findAllWithUserByWorkspaceId(1L)).thenReturn(List.of());
        WorkspaceReviewScope crossTenant = new WorkspaceReviewScope(
            ReviewRepositoryMode.SELECTED,
            ReviewPersonMode.ALL_ELIGIBLE,
            List.of(new ReviewRepositoryTarget("other/tenant", List.of())),
            List.of()
        );

        assertThatThrownBy(() -> service.replace(workspace, crossTenant))
            .isInstanceOf(InvalidReviewCoverageException.class)
            .hasMessageContaining("not monitored by this workspace");
    }

    @Test
    void selectedPersonWithoutThisWorkspaceMembershipIsRejected() {
        when(monitors.findByWorkspaceId(1L)).thenReturn(List.of());
        when(memberships.findAllWithUserByWorkspaceId(1L)).thenReturn(List.of(membership(7L, User.Type.USER)));
        WorkspaceReviewScope crossTenant = new WorkspaceReviewScope(
            ReviewRepositoryMode.ALL_MONITORED,
            ReviewPersonMode.SELECTED,
            List.of(),
            List.of(8L)
        );

        assertThatThrownBy(() -> service.replace(workspace, crossTenant))
            .isInstanceOf(InvalidReviewCoverageException.class)
            .hasMessageContaining("eligible linked workspace member");
    }

    @Test
    void missingBotAndNonMemberSubjectsFailClosed() {
        ReviewSubject human = new ReviewSubject(7L, true);
        ReviewSubject bot = new ReviewSubject(8L, false);
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 7L)).thenReturn(Optional.empty());

        assertThat(service.admits(workspace, "owner/repo", "main", null)).isFalse();
        assertThat(service.admits(workspace, "owner/repo", "main", bot)).isFalse();
        assertThat(service.admits(workspace, "owner/repo", "main", human)).isFalse();
    }

    private static RepositoryToMonitor monitor(long id, String name) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setId(id);
        monitor.setNameWithOwner(name);
        return monitor;
    }

    private static WorkspaceMembership membership(long userId, User.Type type) {
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setUser(user(userId, type));
        return membership;
    }

    private static User user(long id, User.Type type) {
        User user = new User();
        user.setId(id);
        user.setType(type);
        return user;
    }
}
