package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.scm.ScmPersonSourceIdentityContributor;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ScmItemSourceAccessTest extends BaseUnitTest {
    private static final ItemRef ROOT = new ItemRef(1, 2, ScmItemEvents.REPOSITORY, 3L, null, null);
    private static final ItemRef FILE = new ItemRef(1, 4, ScmItemEvents.FILE, null, ROOT.itemId(), "src/a.ts");
    private static final SourceUsePurpose PURPOSE = SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW;

    @Test
    void shouldAuthorizeAnIndexedFileThroughItsCurrentRepositoryWithoutCapturingIt() {
        var fixture = fixture();
        assertThat(fixture.service().permits(FILE, PURPOSE)).isTrue();
    }

    @Test
    void shouldRejectRemovedOrForeignCanonicalItems() {
        var fixture = fixture();
        when(fixture.items().require(FILE))
                .thenThrow(new ItemCaptureException(ItemCaptureCode.ITEM_REMOVED, "Removed"));
        assertThat(fixture.service().permits(FILE, PURPOSE)).isFalse();
        var foreign = new ItemRef(9, FILE.itemId(), FILE.kind(), FILE.sourceId(), FILE.parentId(), FILE.path());
        when(fixture.items().require(foreign))
                .thenThrow(new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "Foreign"));
        assertThat(fixture.service().permits(foreign, PURPOSE)).isFalse();
    }

    @Test
    void shouldRejectARepositoryWhoseCurrentConnectionOrMonitorIdentityChanged() {
        var fixture = fixture();
        doThrow(new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "Changed origin"))
                .when(fixture.access())
                .authorize(1, fixture.repository());
        assertThat(fixture.service().permits(FILE, PURPOSE)).isFalse();
    }

    @Test
    void shouldRecheckEveryCurrentContributorWithoutCreatingAccountsOrCopyReceipts() {
        var fixture = fixture();
        var ref = new ItemRef(1, 5, ScmItemEvents.ISSUE, 6L, ROOT.itemId(), null);
        when(fixture.items().parent(ref, ScmItemEvents.REPOSITORY)).thenReturn(ROOT);
        var issue = new Issue();
        issue.setRepository(fixture.repository());
        when(fixture.issues().findByIdWithAuthorAndRepository(6L)).thenReturn(Optional.of(issue));
        when(fixture.contributors().identitiesForSource(1, "scm.issue", 6))
                .thenReturn(List.of(new PersonIdentity(7, "author", null), new PersonIdentity(7, "commenter", null)));
        when(fixture.identities().accountFor(new PersonIdentity(7L, "author", null))).thenReturn(Optional.of(8L));
        when(fixture.identities().accountFor(new PersonIdentity(7L, "commenter", null))).thenReturn(Optional.of(9L));
        when(fixture.accounts().find(8L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        8, Account.Status.ACTIVE, "Author", AiChoice.CLOUD, null, true)));
        when(fixture.accounts().find(9L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        9, Account.Status.ACTIVE, "Commenter", AiChoice.NO_AI, null, true)));
        assertThat(fixture.service().permits(ref, PURPOSE)).isFalse();
        when(fixture.accounts().find(9L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        9, Account.Status.ACTIVE, "Commenter", AiChoice.IN_HOUSE_ONLY, null, true)));
        assertThat(fixture.service().permits(ref, PURPOSE)).isTrue();
        when(fixture.accounts().find(9L)).thenReturn(Optional.empty());
        assertThat(fixture.service().permits(ref, PURPOSE)).isFalse();
        when(fixture.accounts().find(9L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        9, Account.Status.ACTIVE, "Commenter", AiChoice.IN_HOUSE_ONLY, null, true)));
        when(fixture.suppression().isSuppressed(7, "commenter", null)).thenReturn(true);
        assertThat(fixture.service().permits(ref, PURPOSE)).isFalse();
        when(fixture.suppression().isSuppressed(7, "commenter", null)).thenReturn(false);
        when(fixture.identities().accountFor(new PersonIdentity(7, "commenter", null)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        assertThat(fixture.service().permits(ref, PURPOSE)).isFalse();
    }

    private Fixture fixture() {
        var items = mock(ScmItemAccess.class);
        var repositories = mock(RepositoryRepository.class);
        var issues = mock(IssueRepository.class);
        var reviews = mock(PullRequestReviewRepository.class);
        var access = mock(ScmRepositoryAccess.class);
        var contributors = mock(ScmPersonSourceIdentityContributor.class);
        var suppression = mock(PersonProcessingSuppression.class);
        var identities = mock(PersonIdentityResolver.class);
        var accounts = mock(AccountDirectory.class);
        var repository = new Repository();
        repository.setId(3L);
        lenient().when(items.require(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(items.parent(FILE, ScmItemEvents.REPOSITORY)).thenReturn(ROOT);
        lenient().when(repositories.findById(3L)).thenReturn(Optional.of(repository));
        var service = new ScmItemSourceAccess(
                items, repositories, issues, reviews, access, contributors, suppression, identities, accounts);
        return new Fixture(service, items, issues, access, contributors, suppression, identities, accounts, repository);
    }

    private record Fixture(
            ScmItemSourceAccess service,
            ScmItemAccess items,
            IssueRepository issues,
            ScmRepositoryAccess access,
            ScmPersonSourceIdentityContributor contributors,
            PersonProcessingSuppression suppression,
            PersonIdentityResolver identities,
            AccountDirectory accounts,
            Repository repository) {}
}
