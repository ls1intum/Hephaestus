package de.tum.cit.aet.hephaestus.workspace.context;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceMembershipAutoSeederIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceMembershipRepository membershipRepository;

    @Autowired
    private WorkspaceMembershipService membershipService;

    @Test
    void shouldGrantAdminToFirstUserWhenEnabledAndWorkspaceIsEmpty() {
        User visitor = persistUser("auto-seed-visitor");
        User owner = persistUser("auto-seed-owner");
        Workspace workspace = createWorkspace("auto-seed", "AutoSeed", "autoseed", AccountType.ORG, owner);
        membershipRepository.deleteAll(membershipRepository.findByWorkspace_Id(workspace.getId()));
        WorkspaceMembershipAutoSeeder seeder =
                new WorkspaceMembershipAutoSeeder(membershipRepository, membershipService, true);

        var seeded = seeder.seedFirstUserWhenEmpty(workspace, List.of(visitor));

        assertThat(seeded).get().extracting(membership -> membership.getRole()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(membershipRepository.findByWorkspace_Id(workspace.getId()))
                .singleElement()
                .satisfies(membership -> {
                    assertThat(membership.getId().getUserId()).isEqualTo(visitor.getId());
                    assertThat(membership.getRole()).isEqualTo(WorkspaceRole.ADMIN);
                });
    }
}
