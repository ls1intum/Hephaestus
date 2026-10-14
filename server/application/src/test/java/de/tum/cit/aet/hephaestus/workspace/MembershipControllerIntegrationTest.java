package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.web.PageResponseDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.dto.AssignRoleRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.SetMemberHiddenRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class MembershipControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private IdentityRepository identities;

    @Test
    @WithAdminUser
    void shouldKeepProviderProfileQueriesBoundedForFiftyMembers() {
        var owner = persistUser("profile-batch-owner");
        var workspace = createWorkspace("profile-batch", "Profiles", "profiles", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        connectActivitySource(workspace, owner.getProvider());
        for (int index = 0; index < 50; index++) {
            var member = persistUser("profile-batch-" + index);
            ensureMembership(workspace, member, WorkspaceRole.MEMBER);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long before = statistics.getPrepareStatementCount();
            webTestClient
                    .get()
                    .uri("/workspaces/{slug}/members?size=50", workspace.getWorkspaceSlug())
                    .headers(TestAuthUtils.withCurrentUser())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.content.length()")
                    .isEqualTo(50);
            assertThat(statistics.getPrepareStatementCount() - before).isLessThanOrEqualTo(30);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @Test
    @WithAdminUser
    void shouldReturnNotFoundWhenTheWorkspaceDoesNotExist() {
        webTestClient
                .get()
                .uri("/workspaces/absent-membership-space/members")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404);
    }

    @Test
    @WithAdminUser
    void shouldReturnNotFoundWhenAssigningAnUnknownAccount() {
        var owner = persistUser("missing-account-owner");
        var workspace = createWorkspace("missing-account-space", "Members", "members", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        webTestClient
                .post()
                .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AssignRoleRequestDTO(Long.toString(Long.MAX_VALUE), WorkspaceRole.MEMBER))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404);
    }

    @Test
    @WithAdminUser
    void shouldRejectAnUnknownMemberSortField() {
        var owner = persistUser("invalid-sort-owner");
        var workspace = createWorkspace("invalid-member-sort", "Members", "members", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        webTestClient
                .get()
                .uri("/workspaces/{slug}/members?sort=missingField", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithAdminUser
    void listMembersReturnsAllWorkspaceMembersForAdmin() {
        User owner = persistUser("membership-owner");
        Workspace workspace =
                createWorkspace("membership-space", "Membership Space", "membership", AccountType.ORG, owner);

        var admin = ensureAdminMembership(workspace);

        User member = persistUser("membership-member");
        workspaceMembershipService.createMembership(workspace, accountId(member), WorkspaceRole.MEMBER);

        PageResponseDTO<WorkspaceMembershipDTO> response = webTestClient
                .get()
                .uri("/workspaces/{slug}/members", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<PageResponseDTO<WorkspaceMembershipDTO>>() {})
                .returnResult()
                .getResponseBody();

        var page = Objects.requireNonNull(response);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content())
                .extracting(memberDto -> memberDto.account().id())
                .containsExactlyInAnyOrder(
                        Long.toString(accountId(owner)),
                        Long.toString(accountId(member)),
                        Objects.requireNonNull(admin.getAccountId()).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=-1"})
    @WithAdminUser
    void shouldUseDefaultPaginationWhenParametersAreOutOfRange(String query) {
        Workspace workspace = createWorkspace(
                "pagination-space", "Pagination Space", "pagination", AccountType.ORG, persistUser("pagination-owner"));
        ensureAdminMembership(workspace);

        webTestClient
                .get()
                .uri("/workspaces/{slug}/members" + query, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.number")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(50);
    }

    @Test
    @WithAdminUser
    void shouldAcceptPageSizeAboveCap() {
        Workspace workspace = createWorkspace(
                "large-page-space", "Large Page Space", "large-page", AccountType.ORG, persistUser("large-page-owner"));
        ensureAdminMembership(workspace);

        webTestClient
                .get()
                .uri("/workspaces/{slug}/members?size=101", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.size")
                .isEqualTo(100);
    }

    @Test
    @WithMentorUser
    void adminCanAssignRoleToMember() {
        User owner = persistUser("membership-owner-2");
        Workspace workspace =
                createWorkspace("membership-space-2", "Membership Space 2", "membership2", AccountType.ORG, owner);

        User admin = persistUser("mentor");
        ensureMembership(workspace, admin, WorkspaceRole.ADMIN);

        User targetUser = persistUser("target-user");
        workspaceMembershipService.createMembership(workspace, accountId(targetUser), WorkspaceRole.MEMBER);

        AssignRoleRequestDTO request =
                new AssignRoleRequestDTO(Long.toString(accountId(targetUser)), WorkspaceRole.ADMIN);

        WorkspaceMembershipDTO response = webTestClient
                .post()
                .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceMembershipDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);

        Membership updated = workspaceMembershipRepository
                .findByWorkspace_IdAndId_AccountId(workspace.getId(), accountId(targetUser))
                .orElseThrow();
        assertThat(updated.getRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @WithMentorUser
    void shouldRejectDemotionAndRemovalOfOwnerWhenCallerIsWorkspaceAdmin() {
        User owner = persistUser("protected-owner");
        Workspace workspace = createWorkspace("protected-owner", "Owner", "owner", AccountType.ORG, owner);
        ensureMembership(workspace, persistUser("mentor"), WorkspaceRole.ADMIN);

        webTestClient
                .post()
                .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AssignRoleRequestDTO(Long.toString(accountId(owner)), WorkspaceRole.MEMBER))
                .exchange()
                .expectStatus()
                .isForbidden();
        assertThat(workspaceMembershipService
                        .getMembership(workspace.getId(), accountId(owner))
                        .getRole())
                .isEqualTo(WorkspaceRole.OWNER);

        webTestClient
                .delete()
                .uri("/workspaces/{slug}/members/{userId}", workspace.getWorkspaceSlug(), accountId(owner))
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        assertThat(workspaceMembershipService
                        .getMembership(workspace.getId(), accountId(owner))
                        .getRole())
                .isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @WithMentorUser
    void shouldRejectDemotionAndRemovalWhenCallerIsTheLastOwner() {
        User owner = persistUser("mentor");
        Workspace workspace = createWorkspace("last-owner", "Last owner", "owner", AccountType.ORG, owner);

        webTestClient
                .post()
                .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AssignRoleRequestDTO(Long.toString(accountId(owner)), WorkspaceRole.ADMIN))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        assertThat(workspaceMembershipService
                        .getMembership(workspace.getId(), accountId(owner))
                        .getRole())
                .isEqualTo(WorkspaceRole.OWNER);

        webTestClient
                .delete()
                .uri("/workspaces/{slug}/members/{userId}", workspace.getWorkspaceSlug(), accountId(owner))
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        assertThat(workspaceMembershipService
                        .getMembership(workspace.getId(), accountId(owner))
                        .getRole())
                .isEqualTo(WorkspaceRole.OWNER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @WithMentorUser
    void shouldAllowOwnerToLeaveOwnershipWhenAnotherOwnerHasBeenAssigned(boolean remove) {
        User owner = persistUser("mentor");
        Workspace workspace = createWorkspace("owner-transfer", "Transfer", "owner", AccountType.ORG, owner);
        User successor = persistUser("successor");
        ensureMembership(workspace, successor, WorkspaceRole.MEMBER);

        webTestClient
                .post()
                .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AssignRoleRequestDTO(Long.toString(accountId(successor)), WorkspaceRole.OWNER))
                .exchange()
                .expectStatus()
                .isOk();

        if (remove) {
            webTestClient
                    .delete()
                    .uri("/workspaces/{slug}/members/{userId}", workspace.getWorkspaceSlug(), accountId(owner))
                    .headers(TestAuthUtils.withCurrentUser())
                    .exchange()
                    .expectStatus()
                    .isNoContent();
            assertThat(workspaceMembershipService.getMembership(workspace.getId(), accountId(owner)))
                    .extracting(Membership::getLeftAt)
                    .isNotNull();
        } else {
            webTestClient
                    .post()
                    .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                    .headers(TestAuthUtils.withCurrentUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AssignRoleRequestDTO(Long.toString(accountId(owner)), WorkspaceRole.ADMIN))
                    .exchange()
                    .expectStatus()
                    .isOk();
            assertThat(workspaceMembershipService
                            .getMembership(workspace.getId(), accountId(owner))
                            .getRole())
                    .isEqualTo(WorkspaceRole.ADMIN);
        }
        assertThat(workspaceMembershipService
                        .getMembership(workspace.getId(), successor.getId())
                        .getRole())
                .isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void shouldRetainOneOwnerWhenBothOwnersDemoteThemselvesConcurrently() throws Exception {
        User first = persistUser("concurrent-owner-one");
        User second = persistUser("concurrent-owner-two");
        Workspace workspace = createWorkspace("concurrent-owners", "Owners", "owners", AccountType.ORG, first);
        ensureMembership(workspace, second, WorkspaceRole.OWNER);
        var barrier = new CyclicBarrier(2);
        var responses = new ArrayList<Future<Integer>>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (User owner : List.of(first, second)) {
                TestUserFactory.ensureAccountForUser(accounts, identities, owner);
                var accountId = identities
                        .findActiveByProviderSubject(
                                Objects.requireNonNull(owner.getProvider().getId()),
                                owner.getNativeId().toString(),
                                null)
                        .orElseThrow()
                        .getAccount()
                        .getId();
                String token = "mock-jwt-user-sub-" + Objects.requireNonNull(accountId);
                responses.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return webTestClient
                            .post()
                            .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
                            .headers(headers -> headers.setBearerAuth(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new AssignRoleRequestDTO(Long.toString(accountId(owner)), WorkspaceRole.ADMIN))
                            .exchange()
                            .returnResult(Void.class)
                            .getStatus()
                            .value();
                }));
            }
            assertThat(List.of(
                            responses.get(0).get(20, TimeUnit.SECONDS),
                            responses.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(workspaceMembershipRepository.countByWorkspace_IdAndRole(workspace.getId(), WorkspaceRole.OWNER))
                .isEqualTo(1);
    }

    @Test
    @WithAdminUser
    void updateMemberVisibilityTogglesHiddenFlag() {
        User owner = persistUser("visibility-owner");
        Workspace workspace =
                createWorkspace("visibility-space", "Visibility Space", "visibility", AccountType.ORG, owner);

        ensureAdminMembership(workspace);

        User target = persistUser("visibility-target");
        workspaceMembershipService.createMembership(workspace, accountId(target), WorkspaceRole.MEMBER);

        WorkspaceMembershipDTO hidden = webTestClient
                .patch()
                .uri("/workspaces/{slug}/members/{accountId}/hidden", workspace.getWorkspaceSlug(), accountId(target))
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SetMemberHiddenRequestDTO(true))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceMembershipDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(hidden).isNotNull();
        assertThat(hidden.hidden()).isTrue();
        assertThat(workspaceMembershipRepository.findByWorkspace_IdAndId_AccountId(
                        workspace.getId(), accountId(target)))
                .get()
                .extracting(Membership::isHidden)
                .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void hiddenMemberLeavesWhenOrgSyncOmitsThemAndIsHiddenAgainOnReturn() {

        User owner = persistUser("sync-owner");
        Workspace workspace = createWorkspace("sync-space", "Sync Space", "syncorg", AccountType.ORG, owner);

        User hiddenUser = persistUser("hidden-user");
        workspaceMembershipService.createMembership(workspace, accountId(hiddenUser), WorkspaceRole.MEMBER);
        workspaceMembershipService.updateMemberVisibility(workspace.getId(), accountId(hiddenUser), true);

        User visibleUser = persistUser("visible-user");
        workspaceMembershipService.createMembership(workspace, accountId(visibleUser), WorkspaceRole.MEMBER);

        Map<Long, WorkspaceRole> desiredRoles = new HashMap<>();
        desiredRoles.put(owner.getId(), WorkspaceRole.OWNER);

        workspaceMembershipService.syncWorkspaceMembers(workspace, desiredRoles);

        assertThat(workspaceMembershipRepository.findByWorkspace_IdAndId_AccountId(
                        workspace.getId(), accountId(hiddenUser)))
                .get()
                .extracting(Membership::getLeftAt)
                .isNotNull();
        assertThat(workspaceMembershipRepository.findByWorkspace_IdAndId_AccountId(
                        workspace.getId(), accountId(visibleUser)))
                .get()
                .extracting(Membership::getLeftAt)
                .isNotNull();

        desiredRoles.put(hiddenUser.getId(), WorkspaceRole.MEMBER);
        workspaceMembershipService.syncWorkspaceMembers(workspace, desiredRoles);

        assertThat(workspaceMembershipRepository.findByWorkspace_IdAndId_AccountId(
                        workspace.getId(), accountId(hiddenUser)))
                .get()
                .extracting(Membership::isHidden)
                .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void hiddenFlagIsPreservedWhenOrgSyncUpdatesUnrelatedMemberRole() {

        User owner = persistUser("rolechange-owner");
        Workspace workspace =
                createWorkspace("rolechange-space", "Role Change Space", "rolechange", AccountType.ORG, owner);

        User hiddenUser = persistUser("rolechange-hidden");
        workspaceMembershipService.createMembership(workspace, accountId(hiddenUser), WorkspaceRole.MEMBER);
        workspaceMembershipService.updateMemberVisibility(workspace.getId(), accountId(hiddenUser), true);

        User promoted = persistUser("rolechange-promoted");
        workspaceMembershipService.createMembership(workspace, accountId(promoted), WorkspaceRole.MEMBER);

        Map<Long, WorkspaceRole> desiredRoles = new HashMap<>();
        desiredRoles.put(owner.getId(), WorkspaceRole.OWNER);
        desiredRoles.put(hiddenUser.getId(), WorkspaceRole.MEMBER);
        desiredRoles.put(promoted.getId(), WorkspaceRole.ADMIN);

        workspaceMembershipService.syncWorkspaceMembers(workspace, desiredRoles);

        assertThat(workspaceMembershipRepository.findByWorkspace_IdAndId_AccountId(
                        workspace.getId(), accountId(hiddenUser)))
                .get()
                .extracting(Membership::isHidden)
                .isEqualTo(true);

        assertThat(workspaceMembershipRepository.findByWorkspace_IdAndId_AccountId(
                        workspace.getId(), accountId(promoted)))
                .get()
                .extracting(Membership::getRole)
                .isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @WithMentorUser
    void nonMembersCannotAccessMembershipEndpoints() {

        persistUser("mentor");

        User owner = persistUser("membership-owner-3");
        Workspace workspace =
                createWorkspace("membership-space-3", "Membership Space 3", "membership3", AccountType.ORG, owner);

        webTestClient
                .get()
                .uri("/workspaces/{slug}/members", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }
}
