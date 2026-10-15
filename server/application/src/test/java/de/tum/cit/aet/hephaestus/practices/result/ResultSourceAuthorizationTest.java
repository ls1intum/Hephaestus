package de.tum.cit.aet.hephaestus.practices.result;

import static de.tum.cit.aet.hephaestus.practices.result.ResultTestFixtures.resultBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class ResultSourceAuthorizationTest extends BaseUnitTest {
    final ProductItemQuery items = mock(ProductItemQuery.class);
    final PersonProcessingSuppression suppression = mock(PersonProcessingSuppression.class);
    final PersonIdentityResolver people = mock(PersonIdentityResolver.class);
    final AccountIdentityQuery identities = mock(AccountIdentityQuery.class);
    final AccountDirectory accounts = mock(AccountDirectory.class);
    final ItemSourceAccess source = mock(ItemSourceAccess.class);
    final ClasspathSourceCatalogRegistry catalog = mock(ClasspathSourceCatalogRegistry.class);
    final ProblemRepository problems = mock(ProblemRepository.class);
    final JsonMapper json = new JsonMapper();
    final SourceUsePurpose purpose = SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW;
    final ItemRef item = new ItemRef(1, 2, new ItemKind("scm.pull_request"), 3L, null, null);
    final UUID job = UUID.randomUUID();
    final Result result =
            Result.builder().id(UUID.randomUUID()).workspaceId(1).itemId(2).build();
    final ResultRepository results = mock(ResultRepository.class);
    final ReviewItemRepository assignments = mock(ReviewItemRepository.class);
    final HistoryCustodyQuery custody = mock(HistoryCustodyQuery.class);
    final ResultSourceAuthorization authorization = new ResultSourceAuthorization(
            items,
            assignments,
            custody,
            results,
            json,
            catalog,
            suppression,
            people,
            identities,
            accounts,
            problems,
            mock(ProductItemRepository.class),
            List.of(source));

    @BeforeEach
    void availableSource() {
        Mockito.lenient().when(items.find(1, 2)).thenReturn(Optional.of(item));
        Mockito.lenient().when(source.kinds()).thenReturn(Set.of(item.kind()));
        Mockito.lenient().when(source.permits(item, purpose)).thenReturn(true);
    }

    HistoryCustody receipt(List<PersonCopyIdentity> copies) {
        return new HistoryCustody(true, copies, List.of(), List.of(job));
    }

    @Test
    void developerlessProofUsesItsCapturedCustodyAndCurrentSourceAuthority() {
        assertThat(authorization.authorized(result, receipt(List.of()), purpose))
                .isTrue();
        when(source.permits(item, purpose)).thenReturn(false);
        assertThat(authorization.authorized(result, receipt(List.of()), purpose))
                .isFalse();
    }

    @Test
    void unknownCustodyAndSuppressedProducerAreUnavailable() {
        assertThat(authorization.authorized(
                        result, new HistoryCustody(false, List.of(), List.of(), List.of()), purpose))
                .isFalse();
        when(suppression.isReviewJobSuppressed(job)).thenReturn(true);
        assertThat(authorization.authorized(result, receipt(List.of()), purpose))
                .isFalse();
    }

    @Test
    void exactCapturedNativeIdentityMustStillPermitProcessing() {
        var copy = new PersonCopyIdentity("GITHUB", "https://github.com", "native-7", null);
        when(people.providers())
                .thenReturn(List.of(new PersonIdentityResolver.Provider(8, "GITHUB", "https://github.com")));
        when(identities.resolveAccountId(8L, "native-7", null)).thenReturn(Optional.of(7L));
        when(accounts.find(7))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        7, Account.Status.UNCLAIMED, "Person", AiChoice.CLOUD, null, true)));
        assertThat(authorization.authorized(result, receipt(List.of(copy)), purpose))
                .isTrue();
        when(accounts.find(7))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        7, Account.Status.UNCLAIMED, "Person", AiChoice.NO_AI, null, true)));
        assertThat(authorization.authorized(result, receipt(List.of(copy)), purpose))
                .isFalse();
        when(suppression.isSuppressed(8, "native-7", null)).thenReturn(true);
        assertThat(authorization.authorized(result, receipt(List.of(copy)), purpose))
                .isFalse();
    }

    @Test
    void storedSourceKindUsesCurrentGovernanceRatherThanAnOldDefinition() {
        var evidence = json.createObjectNode();
        evidence.putArray("citations").addObject().put("sourceKind", "scm.pull-request.diff");
        var proof = resultBuilder(result).evidence(evidence).build();
        var kind = new SourceKind("scm.pull-request.diff");
        when(catalog.isSourceUsePermitted(kind, purpose)).thenReturn(false);
        assertThat(authorization.authorized(proof, receipt(List.of()), purpose)).isFalse();
        when(catalog.isSourceUsePermitted(kind, purpose)).thenReturn(true);
        assertThat(authorization.authorized(proof, receipt(List.of()), purpose)).isTrue();
    }

    @Test
    void shouldKeepMigratedHistoryBehindSourceConsentAndErasureChecks() {
        var history = resultBuilder(result)
                .evidence(json.readTree("{\"citations\":[{\"sourceKind\":\"hephaestus.observation-history\"}]}"))
                .evidenceVersion((short) 0)
                .developerId(7L)
                .reviewId(job)
                .build();
        when(results.findByIdAndWorkspaceId(history.getId(), 1)).thenReturn(Optional.of(history));
        when(accounts.find(7))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        7, Account.Status.ACTIVE, "Person", AiChoice.CLOUD, null, true)));
        when(source.permits(item, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW)).thenReturn(true);
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isTrue();
        assertThat(authorization.authorized(history, receipt(List.of()), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        when(assignments.findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(1, job, history.getPracticeId(), 2))
                .thenReturn(Optional.of(ReviewItem.builder()
                        .resultId(history.getId())
                        .jobId(job)
                        .build()));
        when(custody.find(1, List.of(job))).thenReturn(receipt(List.of()));
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isTrue();
        assertThat(authorization.permits(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .isFalse();
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isFalse();
        when(source.permits(item, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW)).thenReturn(false);
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        when(source.permits(item, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW)).thenReturn(true);
        when(suppression.isAccountSuppressed(7)).thenReturn(true);
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        when(suppression.isAccountSuppressed(7)).thenReturn(false);
        when(accounts.find(7))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        7, Account.Status.ACTIVE, "Person", AiChoice.NO_AI, null, true)));
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        when(accounts.find(7))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        7, Account.Status.ACTIVE, "Person", AiChoice.CLOUD, null, true)));
        when(suppression.isReviewJobSuppressed(job)).thenReturn(true);
        assertThat(authorization.permitsHistory(1, history.getId(), SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
    }
}
