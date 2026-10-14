package de.tum.cit.aet.hephaestus.core.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobExecutor;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobLifecycleService;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatService;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorRefusal;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyFence;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentityResolver;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateNonceStore;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessage;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.PracticeModelTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class AccountErasureIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PracticeModelTestFixtures modelFixtures;

    @TempDir
    private Path evidenceRoot;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private PersonDataCopyFence copyFence;

    @Autowired
    private ObjectProvider<ReviewJobLifecycleService> lifecycles;

    @Autowired
    private PersonDataService personData;

    @Autowired
    private PersonProcessingSuppression suppression;

    @Autowired
    private MentorChatService mentor;

    @Autowired
    private ResultMarkRepository invalidations;

    @Autowired
    private PersonDataRegistry registry;

    @Autowired
    private PersonIdentityResolver resolver;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OAuthStateNonceStore oauthNonces;

    @Autowired
    private IssuedJwtRepository issuedTokens;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private IdentityRepository links;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private SlackMessageRepository slackMessages;

    @Autowired
    private SlackThreadRepository slackThreads;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ReviewJobRepository agentJobRepository;

    @Autowired
    private ResultRepository practiceResultRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackCopyRepository feedbackCopyRepository;

    private static final Map<String, String> RETAINED_AFTER_ERASURE = Map.of(
            "account", "the deleted-account tombstone",
            "user", "the anonymised provider profile other people's work still references",
            "identity_restriction", "the permanent native-identity processing control",
            "person_data_request", "the re-resolution preview request itself",
            "person_evidence_copy", "frozen job ids still name the redacted job rows",
            "slack_thread", "frozen thread ids still name the shared thread without the person");

    @BeforeEach
    void clearRows() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldEraseAccountChoicesAndDerivedProofBeforeMembershipWhenTheAccountDeletesItself() {
        var provider = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://self-erasure.example.test"));
        var actor = users.saveAndFlush(TestUserFactory.createUser(42L, "self-erasure", provider));
        var account = accountFor(actor);
        long accountId = Objects.requireNonNull(account.getId());
        account.setAiChoice(AiChoice.NO_AI);
        account.setAiChoiceAt(Instant.now());
        accounts.saveAndFlush(account);
        var workspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("self-erasure"));
        var work = modelFixtures.work(workspace, "self-erasure-practice", "scm.pull_request", 42L);
        var result = modelFixtures.result(work, accountId, "Personal result");
        var feedback = modelFixtures.feedback(
                result, FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, "Personal feedback");
        personData.eraseForAccount(accountId);
        var tombstone = accounts.findById(accountId).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(Account.Status.DELETED);
        assertThat(tombstone.getAiChoice()).isNull();
        assertThat(tombstone.getAiChoiceAt()).isNull();
        assertThat(tombstone.isFeedbackDeliveryEnabled()).isFalse();
        assertThat(practiceResultRepository.findById(result.getId())).isEmpty();
        assertThat(feedbackRepository.findById(feedback.getId())).isEmpty();
        assertThat(memberships.findById_AccountId(accountId)).isEmpty();
        assertThat(links.findByAccount_Id(accountId)).isEmpty();
        assertThat(suppression.isAccountSuppressed(accountId)).isTrue();
        assertThat(suppression.isSuppressed(Objects.requireNonNull(provider.getId()), "42", null))
                .isTrue();
    }

    @Test
    void shouldPreserveUnconfirmedCopyCustodyUntilExternalRemovalIsConfirmed() {
        var fixture = externalInspectionFixture("https://privacy-gitlab.example.com/team/repo/-/merge_requests/19");
        jdbc.update("UPDATE feedback SET state='UNCONFIRMED' WHERE id=?", fixture.feedbackId());
        var preview = personData.preview(
                fixture.administratorId(), null, List.of(new PersonIdentity(fixture.providerId(), "42", null)));
        assertThat(preview.externalDeliveries())
                .contains(new PersonDataContributor.ExternalDelivery(
                        fixture.workspaceId(), "https://privacy-gitlab.example.com/team/repo/-/merge_requests/19"));
        assertThatThrownBy(() -> personData.requestErasure(preview.request().getId(), fixture.administratorId(), false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("un-deliver runbook");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM feedback_copy WHERE feedback_id=?", Long.class, fixture.feedbackId()))
                .isEqualTo(1L);
    }

    @Test
    void shouldRejectAnOldPreviewWhenAProviderCopyReceiptChanges() {
        var fixture = externalInspectionFixture("https://privacy-gitlab.example.com/team/repo/-/merge_requests/19");
        var preview = personData.preview(
                fixture.administratorId(), null, List.of(new PersonIdentity(fixture.providerId(), "42", null)));
        jdbc.update(
                "UPDATE feedback_copy SET"
                        + " state='POSTED',posted_at=CURRENT_TIMESTAMP,external_ref='note-99'"
                        + " WHERE feedback_id=?",
                fixture.feedbackId());
        assertThatThrownBy(() -> personData.export(preview.request().getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("preview scope changed");
        assertThatThrownBy(() -> personData.requestErasure(preview.request().getId(), fixture.administratorId(), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("preview scope changed");
        assertThat(personData.get(preview.request().getId()).request().getState())
                .isEqualTo(PersonDataRequest.State.PREVIEW);
    }

    @Test
    void shouldSuppressSelectedJobsAndRevokeCredentialsWhenErasureFinishes() {
        var fixture = externalInspectionFixture("https://privacy-gitlab.example.com/team/repo/-/merge_requests/19");
        jdbc.update(
                "UPDATE feedback_copy SET state='POSTED',posted_at=CURRENT_TIMESTAMP,external_ref='note-99' WHERE feedback_id=?",
                fixture.feedbackId());
        var preview = personData.preview(
                fixture.administratorId(), null, List.of(new PersonIdentity(fixture.providerId(), "42", null)));
        personData.requestErasure(preview.request().getId(), fixture.administratorId(), true);
        assertThat(suppression.isReviewJobSuppressed(fixture.jobId())).isTrue();
        personData.run(preview.request().getId());
        assertThat(personData.get(preview.request().getId()).request().getState())
                .isEqualTo(PersonDataRequest.State.COMPLETE);
        assertThat(suppression.isReviewJobSuppressed(fixture.jobId())).isFalse();
        assertThat(agentJobRepository.findById(fixture.jobId())).isEmpty();
    }

    private void keepCompositionInputs(DerivedConversation derived) {
        jdbc.update(
                "UPDATE review SET"
                        + " public_review_at=CURRENT_TIMESTAMP"
                        + " WHERE id=(SELECT review_id FROM review_job WHERE id=?)",
                derived.jobId());

        jdbc.update("""
            INSERT INTO review_composition_basis(job_id,workspace_id,admission_attempt,admission_digest,
                basis_version,basis_digest,copy_id,expires_at,payload)
            VALUES (?,?,0,?,1,?,?,CURRENT_TIMESTAMP + INTERVAL '1 hour',
                '{"files":{"record":"ORIGINAL-BASIS-CANARY"}}'::jsonb)
            """, derived.jobId(), derived.workspaceId(), "a".repeat(64), "b".repeat(64), UUID.randomUUID());
    }

    private ExternalInspectionFixture externalInspectionFixture(@Nullable String reviewedWorkUrl) {
        var provider = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://privacy-gitlab.example.com"));
        var target = users.saveAndFlush(TestUserFactory.createUser(42L, "display-only", provider));
        var administrator = new Account("Administrator");
        administrator.setAppRole(Account.AppRole.APP_ADMIN);
        administrator = accounts.saveAndFlush(administrator);
        var workspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("external-inspection"));
        var work = modelFixtures.work(workspace, "external-inspection-" + workspace.getId(), "scm.pull_request", 42L);
        var result = modelFixtures.result(
                work, Objects.requireNonNull(accountFor(target).getId()), "Inspection result");
        var feedback =
                modelFixtures.feedback(result, FeedbackDestination.ON_WORK, FeedbackState.READY, "Prepared feedback");
        var job = work.job();
        jdbc.update("""
            INSERT INTO feedback_send(workspace_id,feedback_id,action,state,attempts,next_attempt_at,created_at,updated_at)
            VALUES (?,?,'POST','PENDING',0,CURRENT_TIMESTAMP + INTERVAL '1 day',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, workspace.getId(), feedback.getId());
        var copyId = FeedbackIds.copy(feedback.getId(), FeedbackCopyKind.SUMMARY, 0);
        jdbc.update(
                """
                INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,state,body,write_key,write_marker,write_started_at,external_url,created_at)
                VALUES (?,?,?,'SUMMARY',0,'WRITE_STARTED',NULL,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)
                """,
                copyId,
                workspace.getId(),
                feedback.getId(),
                copyId.toString(),
                FeedbackIds.writeMarker(copyId),
                reviewedWorkUrl);
        return new ExternalInspectionFixture(
                Objects.requireNonNull(administrator.getId()),
                Objects.requireNonNull(provider.getId()),
                Objects.requireNonNull(workspace.getId()),
                job.getId(),
                feedback.getId());
    }

    private record ExternalInspectionFixture(
            long administratorId, long providerId, long workspaceId, UUID jobId, UUID feedbackId) {}

    @Test
    void erasesTargetAcrossTwoWorkspacesAndEveryRegisteredStoreWithoutTouchingAnotherPerson() throws Exception {
        IdentityProvider scm = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://privacy-gitlab.example.com"));
        IdentityProvider slack =
                providers.saveAndFlush(new IdentityProvider(IdentityProviderType.SLACK, "https://slack.com"));
        Account administrator = new Account("Administrator");
        administrator.setAppRole(Account.AppRole.APP_ADMIN);
        administrator = accounts.saveAndFlush(administrator);
        Account targetAccount = accounts.saveAndFlush(new Account("Target"));
        Account otherAccount = accounts.saveAndFlush(new Account("Other"));
        var targetOAuthBinding = new OAuthStateService.StateBinding(
                1L, IntegrationKind.GITHUB, Instant.now().truncatedTo(ChronoUnit.SECONDS), targetAccount.getId());
        oauthNonces.issue(
                "nonce-credential-canary",
                1L,
                IntegrationKind.GITHUB,
                targetOAuthBinding.issuedAt(),
                targetOAuthBinding.actorAccountId());
        UUID targetTokenId = UUID.randomUUID();
        issuedTokens.saveAndFlush(new IssuedJwt(
                targetTokenId,
                Objects.requireNonNull(targetAccount.getId()),
                Instant.now().plusSeconds(3600)));
        UUID targetSession = UUID.randomUUID();
        UUID otherSession = UUID.randomUUID();
        for (var entry :
                Map.of(targetSession, targetAccount, otherSession, otherAccount).entrySet()) {
            jdbc.update("""
                INSERT INTO client_session(id,account_id,client_kind,client_id,session_expires_at,auth_time,created_at)
                VALUES (?,?,'BROWSER_EXTENSION','privacy-client',CURRENT_TIMESTAMP+INTERVAL '1 hour',
                    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, entry.getKey(), entry.getValue().getId());
            jdbc.update("""
                INSERT INTO client_sign_in_handoff(code_hash,account_id,client_kind,client_id,redirect_uri,
                    code_challenge,session_expires_at,auth_time,expires_at,created_at)
                VALUES (?,?,'BROWSER_EXTENSION','privacy-client','https://client.example.test/callback',
                    'pkce-credential-canary',CURRENT_TIMESTAMP+INTERVAL '1 hour',CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP+INTERVAL '60 seconds',CURRENT_TIMESTAMP)
                """, entry.getKey().toString(), entry.getValue().getId());
        }
        User target = users.saveAndFlush(TestUserFactory.createUser(42L, "target", scm));
        User other = users.saveAndFlush(TestUserFactory.createUser(84L, "other", scm));
        link(targetAccount, scm, "42", null);
        link(otherAccount, scm, "84", null);
        var recorder = new ExactPersonDataCopyRecorder();
        var worker = new EvidenceFolderPersonDataCatalog(
                new FabricLayout(evidenceRoot.toString()),
                jdbc,
                namedJdbc,
                mapper,
                recorder,
                copyFence,
                new DefaultListableBeanFactory().getBeanProvider(ReviewJobExecutor.class),
                lifecycles);
        List<Path> targetFolders = new ArrayList<>();
        List<Path> otherFolders = new ArrayList<>();
        List<DerivedConversation> targetDerived = new ArrayList<>();
        List<DerivedConversation> otherDerived = new ArrayList<>();
        List<Long> sharedThreads = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            String team = "T" + index;
            Workspace workspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("privacy-" + index));
            connections.saveAndFlush(new Connection(
                    workspace,
                    IntegrationKind.SLACK,
                    team,
                    new ConnectionConfig.SlackConfig(team, null, null, Set.of())));
            link(targetAccount, slack, "UTARGET", team);
            link(otherAccount, slack, "UOTHER", team);
            membership(workspace, target);
            membership(workspace, other);
            jdbc.update(
                    """
                    INSERT INTO config_audit_event(occurred_at,workspace_id,actor_kind,actor_account_id,
                        acting_account_id,entity_type,entity_id,action,changed_keys)
                    VALUES (CURRENT_TIMESTAMP,?,'IMPERSONATED',?,?,'WORKSPACE_VISIBILITY',?,'UPDATED',ARRAY['visibility'])
                    """,
                    workspace.getId(),
                    otherAccount.getId(),
                    targetAccount.getId(),
                    workspace.getId().toString());
            jdbc.update(
                    """
                    INSERT INTO config_audit_event(occurred_at,workspace_id,actor_kind,actor_account_id,
                        entity_type,entity_id,action,changed_keys,old_value,new_value)
                    VALUES (CURRENT_TIMESTAMP,?,'USER',?,'WORKSPACE_ROLE',?,'UPDATED',ARRAY['role'],
                        '{"role":"MEMBER","hidden":false}'::jsonb,'{"role":"ADMIN","hidden":false}'::jsonb)
                    """,
                    workspace.getId(),
                    administrator.getId(),
                    target.getId().toString());
            SlackThread shared = slackThread(workspace, "100.1", target, other);
            sharedThreads.add(shared.getId());
            message(workspace, team, "100.1", "100.1", "UTARGET", target, "Target's collected work");
            message(workspace, team, "100.1", "200.1", "UOTHER", other, "Other person's shared reply");
            SlackThread unrelated = slackThread(workspace, "300.1", other);
            message(workspace, team, "300.1", "300.1", "UOTHER", other, "Unrelated conversation");
            targetDerived.add(seedDerivedConversation(workspace, shared.getId(), target));
            otherDerived.add(seedDerivedConversation(workspace, unrelated.getId(), other));
            targetFolders.add(captureMountedEvidence(worker, recorder, targetDerived.getLast(), target));
            otherFolders.add(captureMountedEvidence(worker, recorder, otherDerived.getLast(), other));
        }
        for (var derived : targetDerived) {
            var result = practiceResultRepository
                    .findById(derived.practiceResultIds().getFirst())
                    .orElseThrow();
            invalidations.saveAndFlush(ResultMark.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(result.getWorkspaceId())
                    .resultId(result.getId())
                    .createdById(Objects.requireNonNull(administrator.getId()))
                    .reason("Correction before access request")
                    .createdAt(Instant.now())
                    .build());
        }
        PersonScope otherScope = resolver.resolve(Objects.requireNonNull(otherAccount.getId()), List.of());
        // A shared source row, such as a thread both people joined, changes when the target's part
        // is
        // erased. Compare the rows that belong to the other person alone.
        PersonScope otherPrimaryScope =
                new PersonScope(otherScope.accountId(), otherScope.identities(), otherScope.userIds());
        var otherSelection = registry.select(otherPrimaryScope);
        Map<String, Object> otherRows = new TreeMap<>();
        for (var contributor : registry.stores())
            otherRows.put(
                    contributor.store(),
                    contributor.export(Objects.requireNonNull(otherSelection.get(contributor.store()))));
        long administratorId = Objects.requireNonNull(administrator.getId());
        var preview = personData.preview(administratorId, targetAccount.getId(), List.of());
        UUID requestId = preview.request().getId();
        PersonScope frozen =
                mapper.readValue(Objects.requireNonNull(preview.request().getScopeJson()), PersonScope.class);
        var export = personData.export(requestId);
        Map<String, Long> counts = mapper.readValue(preview.request().getCountsJson(), new TypeReference<>() {});
        assertThat(counts.get("oauth_state_nonce")).isEqualTo(1L);
        assertThat(counts.get("client_session")).isEqualTo(1L);
        assertThat(counts.get("client_sign_in_handoff")).isEqualTo(1L);
        assertThat(export.path("stores").path("client_sign_in_handoff").toString())
                .doesNotContain(targetSession.toString(), "code_hash", "code_challenge");

        assertThat(counts.get("person_evidence_copy")).isEqualTo(2L);
        assertThat(export.path("stores").path("review_job")).hasSize(2);
        assertThat(export.path("stores").path("review_job"))
                .extracting(row -> row.path("id").asString())
                .containsExactlyInAnyOrderElementsOf(targetDerived.stream()
                        .map(derived -> derived.jobId().toString())
                        .toList());
        for (var row : export.path("stores").path("review_job")) {
            assertThat(row.has("capture")).isFalse();
        }
        assertThat(export.toString())
                .doesNotContain("MOUNTED-PROFILE-CANARY", "credential-canary", "unrelated-profile-canary");
        targetFolders.forEach(path -> assertThat(path).exists());
        otherFolders.forEach(path -> assertThat(path).exists());
        assertThat(counts.get("feedback")).isEqualTo(6L);
        assertThat(preview.externalDeliveries()).hasSize(2);
        assertThat(preview.externalDeliveries())
                .allSatisfy(delivery -> assertThat(delivery.locator()).startsWith("https://team-"));
        assertThat(export.path("stores").path("feedback_copy").toString())
                .contains("posted_comment_url", "https://team-");
        assertThatThrownBy(() -> personData.requestErasure(requestId, administratorId, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("un-deliver runbook");
        assertThat(personData.get(requestId).request().getState()).isEqualTo(PersonDataRequest.State.PREVIEW);
        assertThat(counts.get("result")).isEqualTo(4L);
        assertThat(counts.get("result_mark")).isEqualTo(2L);
        assertThat(counts.get("config_audit_event_membership_subject")).isEqualTo(2L);
        assertThat(counts.get("chat_thread")).isEqualTo(2L);
        assertThat(counts.get("chat_message_feedback_copy")).isEqualTo(2L);
        assertThat(counts.get("chat_thread_feedback_runtime_copy")).isEqualTo(2L);
        assertThat(counts.get("chat_thread_runtime_journal")).isEqualTo(2L);
        assertThat(export.path("stores").path("chat_message_feedback_copy").toString())
                .contains("Target guidance copied into another conversation")
                .doesNotContain("Other reply to erased feedback", "user_id", "inputTokens");
        assertThat(counts.get("slack_message")).isEqualTo(2L);
        counts.forEach((store, count) -> assertThat(
                        (long) export.path("stores").path(store).size())
                .as("Frozen preview/export parity for %s", store)
                // Another member's hidden runtime journal is cleared, never
                // exported.
                .isEqualTo(store.equals("chat_thread_runtime_journal") ? 0L : count));
        assertThat(export.path("stores").path("chat_thread").toString()).doesNotContain("session_jsonl");
        assertThat(export.path("stores")
                        .path("chat_message")
                        .get(0)
                        .path("parts")
                        .size())
                .isEqualTo(1);
        assertThat(export.path("stores")
                        .path("chat_message")
                        .get(0)
                        .path("parts")
                        .get(0)
                        .path("text")
                        .asString())
                .isEqualTo("Delivered guidance");
        assertThat(export.path("stores")
                        .path("chat_message")
                        .get(0)
                        .path("metadata")
                        .path("inputTokens")
                        .asLong())
                .isEqualTo(12L);
        assertThat(export.toString())
                .doesNotContain(
                        "Unrelated conversation",
                        "Other person's shared reply",
                        "credential-canary",
                        "unrelated-profile-canary");
        personData.requestErasure(requestId, administratorId, true);
        assertThat(oauthNonces.tryConsume("nonce-credential-canary", targetOAuthBinding))
                .isFalse();
        assertThat(issuedTokens.findActive(targetTokenId, Instant.now())).isEmpty();
        assertThat(issuedTokens.findById(targetTokenId).orElseThrow().getRevokedReason())
                .isEqualTo(IssuedJwt.RevokedReason.ACCOUNT_DELETED);
        assertThat(suppression.isSuppressed(
                        Objects.requireNonNull(target.getProvider().getId()),
                        target.getNativeId().toString(),
                        null))
                .isTrue();
        assertThat(suppression.isSuppressed(
                        Objects.requireNonNull(other.getProvider().getId()),
                        other.getNativeId().toString(),
                        null))
                .isFalse();
        for (var derived : targetDerived) {
            assertThat(mentor.refusal(derived.workspaceId(), targetAccount.getId()))
                    .contains(MentorRefusal.PERSON_ERASED);
            assertThat(mentor.refusal(derived.workspaceId(), otherAccount.getId()))
                    .isNotEqualTo(Optional.of(MentorRefusal.PERSON_ERASED));
        }
        for (long threadId : sharedThreads) {
            long workspaceId = Objects.requireNonNull(
                    jdbc.queryForObject("SELECT workspace_id FROM slack_thread WHERE id=?", Long.class, threadId));
            assertThat(suppression.isSourceSuppressed(workspaceId, "chat.thread", threadId))
                    .isTrue();
            assertThat(suppression.isSourceSuppressed(-1L, "chat.thread", threadId))
                    .isFalse();
        }
        try (var executor = Executors.newSingleThreadExecutor()) {
            var erasure = executor.submit(() -> personData.run(requestId));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!erasure.isDone()) {
                worker.removeLocalRequests();
                if (System.nanoTime() > deadline) throw new AssertionError("Mounted worker did not complete erasure");
                Thread.sleep(20);
            }
            erasure.get(10, TimeUnit.SECONDS);
        }
        targetFolders.forEach(path -> assertThat(path).doesNotExist());
        otherFolders.forEach(path -> assertThat(path).exists());
        for (var derived : targetDerived) {
            var job = agentJobRepository.findById(derived.jobId()).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
            assertThat(job.getCapture()).isNull();
        }

        var receipt = personData.get(requestId).request();
        assertThat(receipt.getState()).isEqualTo(PersonDataRequest.State.COMPLETE);
        assertThat(receipt.getScopeJson()).isNull();
        assertThat(receipt.getSelectionsJson()).isNull();
        Map<String, Long> completed = PersonDataStoreReceipt.counts(mapper, receipt.getCompletedJson());
        Map<String, Long> differentReceipts = new TreeMap<>();
        completed.forEach((store, count) -> {
            if (!count.equals(counts.get(store))) differentReceipts.put(store, count);
        });
        assertThat(completed.keySet()).containsExactlyInAnyOrderElementsOf(counts.keySet());
        assertThat(differentReceipts)
                .as("Receipt counts that differ from the preview")
                .isEmpty();
        // Re-resolve the person as a new request would, and replay the frozen closure: only a store
        // that
        // deliberately keeps an anonymised, tombstone or control row may still select one.
        var again = personData.preview(administratorId, targetAccount.getId(), frozen.identities());
        Map<String, Long> reselected = mapper.readValue(again.request().getCountsJson(), new TypeReference<>() {});
        var frozenAfter = registry.select(frozen);
        Map<String, List<Long>> remaining = new TreeMap<>();
        for (var store : registry.stores()) {
            long resolvedRows = Objects.requireNonNull(reselected.get(store.store()));
            long frozenRows = Objects.requireNonNull(frozenAfter.get(store.store()))
                    .rows()
                    .size();
            if (resolvedRows != 0 || frozenRows != 0) remaining.put(store.store(), List.of(resolvedRows, frozenRows));
        }
        assertThat(remaining)
                .as("Rows still selected after erasure: [re-resolved, frozen]")
                .containsOnlyKeys(RETAINED_AFTER_ERASURE.keySet());
        assertThat(accounts.findById(Objects.requireNonNull(targetAccount.getId()))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(Account.Status.DELETED);
        assertThat(users.findById(target.getId()).orElseThrow().getLogin()).startsWith("erased-");
        for (var derived : targetDerived) {
            assertThat(mentor.refusal(derived.workspaceId(), targetAccount.getId()))
                    .contains(MentorRefusal.PERSON_ERASED);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM config_audit_event WHERE actor_account_id=?"
                                + " AND acting_account_id IS NULL",
                        Long.class,
                        otherAccount.getId()))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM config_audit_event WHERE"
                                + " entity_type='WORKSPACE_ROLE' AND entity_id='ERASED' AND"
                                + " (old_value::jsonb)->>'role'='MEMBER' AND (new_value::jsonb)->>'role'='ADMIN'"
                                + " AND actor_account_id=?",
                        Long.class,
                        administrator.getId()))
                .isEqualTo(2L);
        for (long threadId : sharedThreads) {
            assertThat(jdbc.queryForObject(
                            "SELECT message_count FROM slack_thread WHERE id=?", Integer.class, threadId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT cardinality(participant_account_ids) FROM slack_thread" + " WHERE id=?",
                            Integer.class,
                            threadId))
                    .isEqualTo(1);
        }
        for (var contributor : registry.stores()) {
            // A shared thread's aggregate changes; the other person's participant projection does
            // not.
            assertThat(contributor.export(Objects.requireNonNull(otherSelection.get(contributor.store()))))
                    .as("Another person's %s rows", contributor.store())
                    .isEqualTo(otherRows.get(contributor.store()));
        }
        personData.requestErasure(requestId, administratorId, true);
        personData.run(requestId);
        assertThat(personData.get(requestId).request().getCompletedJson()).isEqualTo(receipt.getCompletedJson());
    }

    @Test
    void shouldRequireExactProviderKeysInsteadOfUsingSlackActorCaches() {
        IdentityProvider scm = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://cache-gitlab.example.org"));
        IdentityProvider slack =
                providers.saveAndFlush(new IdentityProvider(IdentityProviderType.SLACK, "https://slack.com"));
        User target = users.saveAndFlush(TestUserFactory.createUser(42L, "cached-person", scm));
        Workspace workspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("cached-person"));
        membership(workspace, target);
        message(workspace, "TCACHE", "100.1", "100.1", "UCACHE", target, "Captured contribution");
        PersonIdentity gitlabKey = new PersonIdentity(Objects.requireNonNull(scm.getId()), "42", null);
        PersonIdentity slackKey = new PersonIdentity(Objects.requireNonNull(slack.getId()), "UCACHE", "TCACHE");
        var scmScope = resolver.resolve(null, List.of(gitlabKey));
        assertThat(scmScope.userIds()).containsExactly(target.getId());
        assertThat(scmScope.identities()).doesNotContain(slackKey);
        var slackScope = resolver.resolve(null, List.of(slackKey));
        assertThat(slackScope.userIds()).isEmpty();
        assertThat(slackScope.identities()).doesNotContain(gitlabKey);
        assertThat(resolver.resolve(null, List.of(gitlabKey, slackKey)).userIds())
                .containsExactly(target.getId());
    }

    @Test
    void shouldRejectErasureAuthorizedByAnAdministratorWhoseRoleWasRevokedAfterPreview() {
        IdentityProvider scm = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://role-gitlab.example.org"));
        User target = users.saveAndFlush(TestUserFactory.createUser(42L, "target", scm));
        Account administrator = new Account("Administrator");
        administrator.setAppRole(Account.AppRole.APP_ADMIN);
        administrator = accounts.saveAndFlush(administrator);
        long administratorId = Objects.requireNonNull(administrator.getId());
        var preview = personData.preview(
                administratorId, null, List.of(new PersonIdentity(Objects.requireNonNull(scm.getId()), "42", null)));
        administrator.setAppRole(Account.AppRole.USER);
        accounts.saveAndFlush(administrator);
        assertThatThrownBy(() -> personData.requestErasure(preview.request().getId(), administratorId, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(personData.get(preview.request().getId()).request().getState())
                .isEqualTo(PersonDataRequest.State.PREVIEW);
        assertThat(suppression.isSuppressed(
                        Objects.requireNonNull(target.getProvider().getId()),
                        target.getNativeId().toString(),
                        null))
                .isFalse();
        assertThat(users.findById(target.getId()).orElseThrow().getLogin()).isEqualTo("target");
    }

    @Test
    void shouldPreserveActualAdministratorWhenAnotherAdministratorUsesThePersonsOwnPreview() {
        Account person = new Account("Person");
        person.setAppRole(Account.AppRole.APP_ADMIN);
        person = accounts.saveAndFlush(person);
        Account administrator = new Account("Administrator");
        administrator.setAppRole(Account.AppRole.APP_ADMIN);
        administrator = accounts.saveAndFlush(administrator);
        long personId = Objects.requireNonNull(person.getId());
        long administratorId = Objects.requireNonNull(administrator.getId());
        var preview = personData.preview(personId, personId, List.of());
        UUID requestId = preview.request().getId();
        assertThatThrownBy(() -> personData.requestErasure(requestId, personId, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Another instance admin");
        personData.requestErasure(requestId, administratorId, true);
        personData.run(requestId);
        var receipt = personData.get(requestId).request();
        assertThat(receipt.getState()).isEqualTo(PersonDataRequest.State.COMPLETE);
        assertThat(receipt.getAdministratorAccountId()).isEqualTo(administratorId);
        assertThat(accounts.findById(administratorId).orElseThrow().getStatus()).isEqualTo(Account.Status.ACTIVE);
    }

    @Test
    void shouldEraseAffectedProofAndPreserveUnrelatedWorkWhenThePersonAppearsInCopiedEvidence() {
        var provider = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://secondary-evidence.example"));
        var target = users.saveAndFlush(TestUserFactory.createUser(42L, "target", provider));
        var other = users.saveAndFlush(TestUserFactory.createUser(84L, "OTHER-PROFILE-CANARY", provider));
        var administrator = new Account("Administrator");
        administrator.setAppRole(Account.AppRole.APP_ADMIN);
        long administratorId =
                Objects.requireNonNull(accounts.saveAndFlush(administrator).getId());
        List<DerivedConversation> affected = new ArrayList<>();
        List<UUID> copiedJobs = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            var workspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("secondary-" + index));
            var derived = seedDerivedConversation(workspace, 1000L + index, other);
            affected.add(derived);
            copiedJobs.add(derived.jobId());
            keepCompositionInputs(derived);
            // Folder removal has already been acknowledged. Exact provenance remains until its
            // counted
            // person step: the absence of the folder must not hide the job that copied the person.
            jdbc.update(
                    """
                    INSERT INTO person_evidence_copy(id,workspace_id,job_id,store_id,state,payload)
                    VALUES (?,?,?,?,'ERASED',CAST(? AS jsonb))
                    """,
                    UUID.randomUUID(),
                    workspace.getId(),
                    derived.jobId(),
                    UUID.randomUUID(),
                    mapper.writeValueAsString(Map.of(
                            "identities",
                            List.of(new PersonCopyIdentity("GITLAB", provider.getServerUrl(), "42", null)),
                            "repositories",
                            List.of())));
        }
        var unrelatedWorkspace = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("secondary-keep"));
        var unrelated = seedDerivedConversation(unrelatedWorkspace, 2000L, other);
        keepCompositionInputs(unrelated);
        var preview = personData.preview(
                administratorId,
                null,
                List.of(new PersonIdentity(Objects.requireNonNull(provider.getId()), "42", null)));
        UUID requestId = preview.request().getId();
        var scope = mapper.readValue(Objects.requireNonNull(preview.request().getScopeJson()), PersonScope.class);
        assertThat(scope.derivedJobIds()).containsExactlyInAnyOrderElementsOf(copiedJobs);
        var export = personData.export(requestId);
        assertThat(export.toString())
                .doesNotContain("Result title", "Practice guidance for developer", "Delivered guidance");
        assertThat(export.path("stores").path("person_evidence_copy"))
                .extracting(row -> row.path("jobId").asString())
                .containsExactlyInAnyOrderElementsOf(
                        copiedJobs.stream().map(UUID::toString).toList());
        Map<String, Long> counts = mapper.readValue(preview.request().getCountsJson(), new TypeReference<>() {});
        assertThat(counts.get("person_evidence_copy")).isEqualTo(2L);
        assertThat(counts.get("review_composition_basis")).isEqualTo(2L);
        assertThat(counts.get("review")).isNotNull();
        assertThat(export.path("stores").path("review_composition_basis").toString())
                .doesNotContain("ORIGINAL-BASIS-CANARY");
        assertThat(export.path("stores").path("review_job"))
                .extracting(row -> row.path("id").asString())
                .containsExactlyInAnyOrderElementsOf(
                        copiedJobs.stream().map(UUID::toString).toList());
        assertThat(export.path("stores").path("review_job"))
                .allSatisfy(row -> assertThat(row.has("capture")).isFalse());
        assertThat(export.path("stores").path("chat_thread_runtime_journal")).isEmpty();
        assertThat(export.toString())
                .doesNotContain(
                        "OTHER-PROFILE-CANARY",
                        "credential-canary",
                        "Practice guidance for developer",
                        "context/conversation.json");
        UUID laterThread = seedJournal(
                        workspaces.findById(affected.getFirst().workspaceId()).orElseThrow(), other)
                .getId();
        personData.requestErasure(requestId, administratorId, true);
        personData.run(requestId);
        assertThat(personData.get(requestId).request().getState()).isEqualTo(PersonDataRequest.State.COMPLETE);
        for (var derived : affected) {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM review WHERE id=(SELECT review_id FROM"
                                    + " review_job WHERE id=?) AND public_review_at IS NOT"
                                    + " NULL",
                            Integer.class,
                            derived.jobId()))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM review_composition_basis WHERE job_id=?",
                            Integer.class,
                            derived.jobId()))
                    .isZero();
            assertThat(practiceResultRepository.findAllById(derived.practiceResultIds()))
                    .isEmpty();
            assertThat(feedbackRepository.findById(derived.preparedId())).isEmpty();
            assertThat(feedbackRepository.findById(derived.deliveredId())).isEmpty();
            assertThat(chatMessageRepository
                            .findById(derived.messageId())
                            .orElseThrow()
                            .getParts()
                            .toString())
                    .contains("This feedback was erased.");
            assertThat(agentJobRepository
                            .findById(derived.jobId())
                            .orElseThrow()
                            .getCapture())
                    .isNull();
            assertThat(jdbc.queryForObject(
                            "SELECT length(session_jsonl) = 0 FROM chat_thread t JOIN"
                                    + " chat_message m ON m.thread_id=t.id WHERE m.id=?",
                            Boolean.class,
                            derived.messageId()))
                    .isTrue();
        }
        assertThat(jdbc.queryForObject(
                        "SELECT length(session_jsonl) = 0 FROM chat_thread WHERE id=?", Boolean.class, laterThread))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM person_evidence_copy WHERE" + " payload<>'{}'::jsonb", Long.class))
                .isZero();
        assertThat(agentJobRepository.findById(unrelated.jobId()).orElseThrow().getCapture())
                .isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM review_composition_basis WHERE job_id=?",
                        Integer.class,
                        unrelated.jobId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT length(session_jsonl) > 0 FROM chat_thread t JOIN"
                                + " chat_message m ON m.thread_id=t.id WHERE m.id=?",
                        Boolean.class,
                        unrelated.messageId()))
                .isTrue();
        assertThat(users.findById(other.getId()).orElseThrow().getLogin()).isEqualTo("OTHER-PROFILE-CANARY");
        assertThat(suppression.isSuppressed(
                        Objects.requireNonNull(target.getProvider().getId()),
                        target.getNativeId().toString(),
                        null))
                .isTrue();
        personData.run(requestId);
        assertThat(personData.get(requestId).request().getState()).isEqualTo(PersonDataRequest.State.COMPLETE);
    }

    private void link(Account account, IdentityProvider provider, String subject, @Nullable String team) {
        Identity link = new Identity();
        link.setAccount(account);
        link.setProviderId(Objects.requireNonNull(provider.getId()));
        link.setProviderUserKey(subject);
        link.setTeamKey(Objects.requireNonNullElse(team, ""));
        links.saveAndFlush(link);
    }

    private Account accountFor(User user) {
        var existing = links.findActiveByProviderSubject(
                Objects.requireNonNull(user.getProvider().getId()),
                user.getNativeId().toString(),
                null);
        if (existing.isPresent()) return existing.get().getAccount();
        var account = accounts.saveAndFlush(new Account(user.getLogin()));
        link(account, user.getProvider(), user.getNativeId().toString(), null);
        return account;
    }

    private void membership(Workspace workspace, User user) {
        Membership membership = new Membership();
        membership.setWorkspace(workspace);
        membership.setId(new Membership.Id(workspace.getId(), accountFor(user).getId()));
        memberships.saveAndFlush(membership);
    }

    private SlackThread slackThread(Workspace workspace, String root, User... participants) {
        SlackThread thread = new SlackThread();
        thread.setWorkspaceId(workspace.getId());
        thread.setSlackChannelId("C1");
        thread.setSlackThreadTs(root);
        thread.setParticipantAccountIds(Arrays.stream(participants)
                .mapToLong(user -> Objects.requireNonNull(accountFor(user).getId()))
                .toArray());
        thread.setMessageCount(participants.length);
        return slackThreads.saveAndFlush(thread);
    }

    private void message(
            Workspace workspace,
            String team,
            String root,
            String timestamp,
            String nativeUser,
            User member,
            String text) {
        SlackMessage message = new SlackMessage();
        message.setWorkspaceId(workspace.getId());
        message.setSlackTeamId(team);
        message.setSlackChannelId("C1");
        message.setSlackThreadTs(root);
        message.setSlackTs(timestamp);
        message.setAuthorSlackUserId(nativeUser);
        message.setAuthorAccountId(accountFor(member).getId());
        message.setText(text);
        slackMessages.saveAndFlush(message);
    }

    private Path captureMountedEvidence(
            EvidenceFolderPersonDataCatalog worker,
            PersonDataCopyRecorder recorder,
            DerivedConversation derived,
            User owner)
            throws Exception {
        var job = agentJobRepository.findById(derived.jobId()).orElseThrow();
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setWorkerId("mounted-privacy-worker");
        agentJobRepository.saveAndFlush(job);
        var files = new JobEvidenceFiles(
                new FabricLayout(evidenceRoot.toString()), agentJobRepository, Clock.systemUTC(), worker);
        files.beginPersonCapture(job);
        recorder.recordIdentity(new PersonCopyIdentity(
                owner.getProvider().getType().name(),
                owner.getProvider().getServerUrl(),
                owner.getNativeId().toString(),
                null));
        try (var prepared = files.prepare(
                job,
                new PreparedEvidence(
                        Map.of("context/person.json", "MOUNTED-PROFILE-CANARY".getBytes(StandardCharsets.UTF_8)), null),
                null)) {
            assertThat(prepared.filesOnDisk()).isNotEmpty();
        }
        return evidenceRoot
                .resolve("jobs")
                .resolve(Long.toString(job.getWorkspaceId()))
                .resolve(job.getId().toString());
    }

    private record DerivedConversation(
            List<UUID> practiceResultIds,
            UUID preparedId,
            UUID deliveredId,
            UUID messageId,
            UUID jobId,
            long workspaceId) {}

    private ChatThread seedJournal(Workspace workspace, User owner) {
        ChatThread chatThread = new ChatThread();
        chatThread.setSessionJsonl(
                "{\"type\":\"toolResult\",\"body\":\"runtime-credential-canary unrelated-profile-canary\"}"
                        .getBytes(StandardCharsets.UTF_8));
        chatThread.setId(UUID.randomUUID());
        chatThread.setWorkspace(workspace);
        chatThread.setAccountId(Objects.requireNonNull(accountFor(owner).getId()));
        return chatThreadRepository.save(chatThread);
    }

    private DerivedConversation seedDerivedConversation(Workspace workspace, long threadId, User owner) {
        ChatThread chatThread = seedJournal(workspace, owner);
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setThread(chatThread);
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setStatus(ChatMessage.Status.completed);
        message.setParts(mapper.valueToTree(List.of(
                Map.of("type", "text", "text", "Delivered guidance", "privateCredential", "credential-canary"),
                Map.of(
                        "type",
                        "tool-fetchContext",
                        "toolCallId",
                        "context-call",
                        "input",
                        Map.of("authorization", "credential-canary"),
                        "output",
                        Map.of("profile", "unrelated-profile-canary")))));
        message.setMetadata(mapper.valueToTree(Map.of(
                "inputTokens",
                12,
                "error",
                Map.of("secret", "credential-canary"),
                "toolCalls",
                List.of(Map.of("profile", "unrelated-profile-canary")))));
        chatMessageRepository.save(message);

        var work = modelFixtures.work(
                workspace, "conv-practice-" + workspace.getId() + "-" + owner.getId(), "chat.thread", threadId);
        var job = work.job();
        job.setCapture(mapper.valueToTree(Map.of(
                "sourcePath",
                "context/conversation.json",
                "author",
                owner.getNativeId().toString(),
                "privateCredential",
                "credential-canary",
                "sourceText",
                "unrelated-profile-canary")));
        job.setOutput(mapper.valueToTree(Map.of("sourceText", "unrelated-profile-canary")));
        agentJobRepository.save(job);
        var first = modelFixtures.result(
                work, Objects.requireNonNull(accountFor(owner).getId()), "Result title");
        var second = modelFixtures.result(
                work, Objects.requireNonNull(accountFor(owner).getId()), "Result title");
        List<UUID> practiceResultIds = List.of(first.getId(), second.getId());
        var prepared = modelFixtures.feedback(
                first,
                FeedbackDestination.CONVERSATION,
                FeedbackState.READY,
                "Practice guidance for developer " + owner.getId());
        var delivered = modelFixtures.feedback(
                second,
                FeedbackDestination.CONVERSATION,
                FeedbackState.DELIVERED,
                "Practice guidance for developer " + owner.getId());
        UUID copyId = FeedbackIds.copy(delivered.getId(), FeedbackCopyKind.CHAT_TURN, 0);
        feedbackCopyRepository.save(FeedbackCopy.builder()
                .id(copyId)
                .writeKey(copyId.toString())
                .writeMarker(FeedbackIds.writeMarker(copyId))
                .workspaceId(workspace.getId())
                .feedbackId(delivered.getId())
                .kind(FeedbackCopyKind.CHAT_TURN)
                .state(FeedbackCopyState.POSTED)
                .body("Delivered guidance")
                .writeStartedAt(Instant.now())
                .chatMessageId(message.getId())
                .postedAt(Instant.now())
                .createdAt(Instant.now())
                .build());
        return new DerivedConversation(
                practiceResultIds,
                prepared.getId(),
                delivered.getId(),
                message.getId(),
                job.getId(),
                workspace.getId());
    }
}
