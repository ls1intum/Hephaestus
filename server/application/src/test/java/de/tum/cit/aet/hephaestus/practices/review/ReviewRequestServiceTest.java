package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.RoleHolder;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.ItemFixtures;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.authorization.WorkspaceAccessService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewRequestServiceTest extends BaseUnitTest {
    final AccountIdentityQuery identities = mock(AccountIdentityQuery.class);
    final WorkspaceAccessService access = mock(WorkspaceAccessService.class);
    final ReviewRequestService service = new ReviewRequestService(
            mock(ProductItemQuery.class),
            identities,
            access,
            mock(ReviewService.class),
            mock(ReviewRequestPolicy.class),
            mock(MembershipRepository.class));

    @Test
    void authorAndAssigneeCanRequestTheirOwnItem() {
        when(identities.resolveActiveAccountId(1L, "native-user", null)).thenReturn(Optional.of(7L));
        for (var role : List.of(AboutRole.AUTHOR, AboutRole.ASSIGNEE))
            assertThat(service.mayRequest(1, snapshot(1, role, true), 7)).isTrue();
    }

    @Test
    void reviewerAndMergerCannotRequestAsAnAuthor() {
        for (var role : List.of(AboutRole.REVIEWER, AboutRole.MERGER))
            assertThat(service.mayRequest(1, snapshot(1, role, true), 7)).isFalse();
        verifyNoInteractions(identities);
    }

    @Test
    void unresolvedIdentityDoesNotBecomeADeveloperClaim() {
        when(identities.resolveActiveAccountId(1L, "native-user", null)).thenReturn(Optional.empty());
        assertThat(service.mayRequest(1, snapshot(1, AboutRole.AUTHOR, true), 7))
                .isFalse();
    }

    @Test
    void botsCannotRequestAsPeople() {
        assertThat(service.mayRequest(1, snapshot(1, AboutRole.AUTHOR, false), 7))
                .isFalse();
        verifyNoInteractions(identities);
    }

    @Test
    void workspaceAdminCanRequestEvenWithoutAPersonRole() {
        when(access.isAdmin()).thenReturn(true);
        assertThat(service.mayRequest(1, snapshot(1, AboutRole.REVIEWER, true), 7))
                .isTrue();
    }

    @Test
    void foreignWorkspaceIsRejectedEvenForAnAdmin() {
        assertThat(service.mayRequest(1, snapshot(2, AboutRole.AUTHOR, true), 7))
                .isFalse();
        verifyNoInteractions(access, identities);
    }

    ItemSnapshot snapshot(long ws, AboutRole role, boolean human) {
        var person = new PersonIdentity(1, "native-user", null);
        return ItemFixtures.snapshot(new ItemRef(ws, 3, new ItemKind("scm.pull_request"), 3L, null, null), Map.of())
                .integration(new IntegrationRef(IntegrationKind.GITHUB, 1, "Test", null))
                .roles(List.of(new RoleHolder(role, person, human)))
                .label(ItemFixtures.label()
                        .itemId(3)
                        .title("Item")
                        .noun("Pull request")
                        .number(1)
                        .container("Repository")
                        .integration(IntegrationKind.GITHUB)
                        .build())
                .build();
    }
}
