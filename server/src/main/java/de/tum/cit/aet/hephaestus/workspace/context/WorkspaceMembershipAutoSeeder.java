package de.tum.cit.aet.hephaestus.workspace.context;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@ConditionalOnServerRole
@Component
public class WorkspaceMembershipAutoSeeder {

    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceMembershipService membershipService;
    private final boolean enabled;

    public WorkspaceMembershipAutoSeeder(
        WorkspaceMembershipRepository membershipRepository,
        WorkspaceMembershipService membershipService,
        @Value("${hephaestus.workspace.auto-seed-membership:false}") boolean enabled
    ) {
        this.membershipRepository = membershipRepository;
        this.membershipService = membershipService;
        this.enabled = enabled;
    }

    public Optional<WorkspaceMembership> seedFirstUserWhenEmpty(Workspace workspace, Collection<User> users) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<User> firstUser = users
            .stream()
            .filter(user -> user != null && user.getId() != null)
            .findFirst();
        if (firstUser.isEmpty() || membershipRepository.countByWorkspace_Id(workspace.getId()) != 0) {
            return Optional.empty();
        }
        return firstUser.map(user -> membershipService.createMembership(workspace, user.getId(), WorkspaceRole.ADMIN));
    }
}
