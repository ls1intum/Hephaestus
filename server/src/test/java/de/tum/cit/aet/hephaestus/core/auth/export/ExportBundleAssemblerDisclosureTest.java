package de.tum.cit.aet.hephaestus.core.auth.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.access.DataAccessEvent;
import de.tum.cit.aet.hephaestus.core.audit.access.DataAccessEventRepository;
import de.tum.cit.aet.hephaestus.core.audit.access.DataAccessEvents;
import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
import de.tum.cit.aet.hephaestus.core.auth.AccountService;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery.WorkspaceMembershipView;
import de.tum.cit.aet.hephaestus.core.auth.spi.ExternalActorQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** The Art. 15(1)(c) section of the GDPR export: who was shown this person's practice data. */
class ExportBundleAssemblerDisclosureTest extends BaseUnitTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final Long SUBJECT_ACTOR_ID = 100L;
    private static final Long WORKSPACE_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock
    private AccountService accountService;

    @Mock
    private AccountFeatureRepository accountFeatureRepository;

    @Mock
    private AuthEventRepository authEventRepository;

    @Mock
    private AccountWorkspaceMembershipQuery workspaceMembershipQuery;

    @Mock
    private AccountPreferencesQuery preferencesQuery;

    @Mock
    private GitProviderRegistry gitProviderRegistry;

    @Mock
    private DataAccessEventRepository dataAccessEventRepository;

    @Mock
    private ExternalActorQuery externalActorQuery;

    private ExportBundleAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ExportBundleAssembler(
            accountService,
            accountFeatureRepository,
            authEventRepository,
            workspaceMembershipQuery,
            preferencesQuery,
            gitProviderRegistry,
            dataAccessEventRepository,
            externalActorQuery,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        Account account = new Account("Subject");
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        account.setStatus(Account.Status.ACTIVE);
        when(accountService.requireById(ACCOUNT_ID)).thenReturn(account);
        when(accountService.activeIdentities(ACCOUNT_ID)).thenReturn(List.of(identityLink(SUBJECT_ACTOR_ID)));
        when(accountFeatureRepository.findFlagsByAccountId(ACCOUNT_ID)).thenReturn(List.of());
        when(authEventRepository.findByAccountSince(anyLong(), any(Instant.class))).thenReturn(List.of());
        lenient().when(preferencesQuery.preferencesForLogin(any())).thenReturn(Optional.empty());
        lenient().when(gitProviderRegistry.providerTypeName(anyLong())).thenReturn("GITHUB");
        when(workspaceMembershipQuery.membershipsForLogins(anySet())).thenReturn(
            List.of(new WorkspaceMembershipView(WORKSPACE_ID, "acme", "Acme", "MEMBER", SUBJECT_ACTOR_ID))
        );
    }

    private static IdentityLink identityLink(Long externalActorId) {
        IdentityLink link = new IdentityLink();
        link.setProviderId(1L);
        link.setSubject("1234");
        link.setUsernameAtSignup("subject");
        link.setExternalActorId(externalActorId);
        return link;
    }

    private static DataAccessEvent event(Long actorUserId, Long subjectUserId, DataAccessResourceType type) {
        return DataAccessEvents.of(WORKSPACE_ID, actorUserId, subjectUserId, type, NOW);
    }

    private void givenDisclosures(DataAccessEvent... events) {
        when(
            dataAccessEventRepository.findForSubject(anyCollection(), anyCollection(), any(Pageable.class))
        ).thenReturn(List.of(events));
    }

    @Test
    @DisplayName("a report a mentor opened is disclosed, naming the mentor")
    void namesTheRecipientOfAReportView() {
        givenDisclosures(event(900L, SUBJECT_ACTOR_ID, DataAccessResourceType.PRACTICE_REPORT));
        when(externalActorQuery.loginsByActorIds(anyCollection())).thenReturn(Map.of(900L, "mentor-mo"));

        List<ExportBundle.DataDisclosure> disclosures = assembler.assemble(ACCOUNT_ID).dataDisclosures();

        assertThat(disclosures)
            .singleElement()
            .satisfies(disclosure -> {
                assertThat(disclosure.occurredAt()).isEqualTo(NOW);
                assertThat(disclosure.workspaceSlug()).isEqualTo("acme");
                assertThat(disclosure.resourceType()).isEqualTo("PRACTICE_REPORT");
                assertThat(disclosure.recipientLogin()).isEqualTo("mentor-mo");
            });
    }

    @Test
    @DisplayName("a roster view in the subject's workspace is disclosed too")
    void includesBulkRosterViewsForWorkspacesTheSubjectBelongsTo() {
        givenDisclosures(event(900L, null, DataAccessResourceType.PRACTICE_ROSTER));
        when(externalActorQuery.loginsByActorIds(anyCollection())).thenReturn(Map.of(900L, "mentor-mo"));

        assertThat(assembler.assemble(ACCOUNT_ID).dataDisclosures())
            .singleElement()
            .extracting(ExportBundle.DataDisclosure::resourceType)
            .isEqualTo("PRACTICE_ROSTER");
    }

    @Test
    @DisplayName("an erased recipient leaves the disclosure standing, without a name")
    void survivesAnErasedRecipient() {
        givenDisclosures(event(null, SUBJECT_ACTOR_ID, DataAccessResourceType.PRACTICE_REPORT));
        lenient().when(externalActorQuery.loginsByActorIds(anyCollection())).thenReturn(Map.of());

        assertThat(assembler.assemble(ACCOUNT_ID).dataDisclosures())
            .singleElement()
            .satisfies(disclosure -> {
                assertThat(disclosure.resourceType()).isEqualTo("PRACTICE_REPORT");
                assertThat(disclosure.recipientLogin()).isNull();
            });
    }

    @Test
    @DisplayName("a recipient the actor mirror no longer resolves is left unnamed rather than dropped")
    void keepsUnresolvableRecipients() {
        givenDisclosures(event(900L, SUBJECT_ACTOR_ID, DataAccessResourceType.PRACTICE_REPORT));
        when(externalActorQuery.loginsByActorIds(anyCollection())).thenReturn(Map.of());

        assertThat(assembler.assemble(ACCOUNT_ID).dataDisclosures())
            .singleElement()
            .extracting(ExportBundle.DataDisclosure::recipientLogin)
            .isNull();
    }

    @Test
    @DisplayName("a failing disclosure lookup empties the section, not the export")
    void failsSoftSoTheRestOfTheExportStillReaches() {
        when(dataAccessEventRepository.findForSubject(anyCollection(), anyCollection(), any(Pageable.class))).thenThrow(
            new IllegalStateException("boom")
        );

        ExportBundle bundle = assembler.assemble(ACCOUNT_ID);

        assertThat(bundle.dataDisclosures()).isEmpty();
        assertThat(bundle.account().id()).isEqualTo(ACCOUNT_ID);
    }
}
