package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewRepositoryPreparer;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadProjection;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadProjection.MessageKey;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection.DocumentAccess;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewMemberAiPolicy;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.security.CurrentScmIdentityHolder;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.MemberAiChoice;
import de.tum.cit.aet.hephaestus.workspace.spi.MemberAiPreferences;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CitedSourceAccessTest extends BaseUnitTest {
    private static EvidenceFolderPersonDataCatalog personCopies() {
        AutoCloseable released = () -> {};
        return Mockito.mock(
                EvidenceFolderPersonDataCatalog.class,
                invocation -> invocation.getMethod().getName().equals("finishCapture")
                        ? released
                        : Answers.RETURNS_DEFAULTS.answer(invocation));
    }

    @TempDir
    Path root;

    private final JsonMapper mapper = new JsonMapper();
    private final ConversationThreadProjection conversations = mock(ConversationThreadProjection.class);
    private final DocumentProjection documents = mock(DocumentProjection.class);
    private final ReviewRepositoryPreparer repositories = mock(ReviewRepositoryPreparer.class);
    private final ReviewHistoryContentSource history = mock(ReviewHistoryContentSource.class);
    private final ReviewMemberAiPolicy memberPolicy = mock(ReviewMemberAiPolicy.class);
    private final MemberAiPreferences preferences = mock(MemberAiPreferences.class);
    private final WorkspaceMembershipRepository memberships = mock(WorkspaceMembershipRepository.class);
    private final PersonProcessingSuppression suppression = mock(PersonProcessingSuppression.class);

    private AgentJob job() {
        var workspace = new Workspace();
        workspace.setId(1L);
        var job = new AgentJob();
        job.setWorkspace(workspace);
        job.setId(UUID.randomUUID());
        job.setWorkerId("worker");
        return job;
    }

    private CitedSourceAccess access(JobEvidenceFiles files) {
        return new CitedSourceAccess(
                conversations,
                documents,
                repositories,
                history,
                files,
                mapper,
                memberPolicy,
                preferences,
                memberships,
                suppression);
    }

    @Test
    void bindsTheFrozenMessageIdentityNotAnAgentSuppliedReferenceAndRechecksConsent() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        String path = "context/chat/C1/2026-10.jsonl";
        byte[] bytes = "{\"channel\":\"C1\",\"ts\":\"1.000001\",\"text\":\"quote\",\"synced_at\":null}\n"
                .getBytes(StandardCharsets.UTF_8);
        try (var prepared = files.prepare(job, new PreparedEvidence(Map.of(path, bytes), null), null)) {
            assertThat(prepared.filesOnDisk()).containsKey(path);
            var citation = mapper.createObjectNode()
                    .put("artifactPath", path)
                    .put("startLine", 1)
                    .put("endLine", 1);
            citation.putObject("sourceReference").put("channel", "foreign");
            when(conversations.readableMessages(1L, Set.of(new MessageKey("C1", "1.000001"))))
                    .thenReturn(Set.of(new MessageKey("C1", "1.000001")));
            var access = access(files);
            access.bind(job, citation, ProvenanceDigest.sha256Hex(bytes));
            assertThat(citation.path("sourceReference")
                            .path("records")
                            .get(0)
                            .path("channel")
                            .asString())
                    .isEqualTo("C1");
            when(conversations.readableMessages(1L, Set.of(new MessageKey("C1", "1.000001"))))
                    .thenReturn(Set.of());
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            assertThatThrownBy(() -> access.bind(job, citation, ProvenanceDigest.sha256Hex(bytes)))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("no longer permitted");
        }
    }

    @Test
    void normalizedThreadCannotBypassAWithdrawnMessage() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        String path = "context/conversation_thread.json";
        byte[] bytes =
                "{\"channel\":\"C1\",\"messages\":[{\"ts\":\"1\"},{\"ts\":\"2\"}]}".getBytes(StandardCharsets.UTF_8);
        try (var prepared = files.prepare(job, new PreparedEvidence(Map.of(path, bytes), null), null)) {
            assertThat(prepared.filesOnDisk()).containsKey(path);
            var citation = mapper.createObjectNode().put("artifactPath", path);
            when(conversations.readableMessages(1L, Set.of(new MessageKey("C1", "1"), new MessageKey("C1", "2"))))
                    .thenReturn(Set.of(new MessageKey("C1", "1"), new MessageKey("C1", "2")));
            var access = access(files);
            access.bind(job, citation, ProvenanceDigest.sha256Hex(bytes));
            assertThat(citation.path("sourceReference").path("records")).hasSize(2);
            when(conversations.readableMessages(1L, Set.of(new MessageKey("C1", "1"), new MessageKey("C1", "2"))))
                    .thenReturn(Set.of(new MessageKey("C1", "1")));
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
        }
    }

    @Test
    void normalizedDocumentCannotBypassErasure() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        job.setMetadata(mapper.createObjectNode().put("docs_document_id", 7L));
        when(documents.documentAccessById(Mockito.eq(1L), any()))
                .thenReturn(List.of(new DocumentAccess(7L, null, "c", "d", false, true)));
        var citation = mapper.createObjectNode().put("artifactPath", "context/document.md");
        var access = access(files);
        access.bind(job, citation, "a".repeat(64));
        assertThat(citation.path("sourceReference")
                        .path("records")
                        .get(0)
                        .path("id")
                        .asLong())
                .isEqualTo(7L);
        when(documents.documentAccessById(Mockito.eq(1L), any())).thenReturn(List.of());
        assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isFalse();
    }

    @Test
    void normalizedScmCannotBypassRepositoryRemoval() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        job.setMetadata(mapper.createObjectNode().put("repository_id", 2L));
        var citation = mapper.createObjectNode()
                .put("artifactPath", "context/comments.json")
                .put("sourceKind", "scm.pull-request.discussion");
        when(repositories.permittedRepositories(1L)).thenReturn(List.of());
        assertThatThrownBy(() -> access(files).bind(job, citation, "a".repeat(64)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("no longer permitted");
        assertThat(citation.path("sourceReference")
                        .path("records")
                        .get(0)
                        .path("id")
                        .asLong())
                .isEqualTo(2L);
    }

    @Test
    void composedViewsCannotBypassCanonicalRecordAuthorization() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        for (String path : List.of("inputs/history/observations.json", "context/project_inventory.json")) {
            var citation = mapper.createObjectNode().put("artifactPath", path);
            assertThatThrownBy(() -> access(files).bind(job(), citation, "a".repeat(64)))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("canonical");
            citation.putObject("sourceReference")
                    .putArray("records")
                    .addObject()
                    .put("type", "person");
            assertThat(access(files).permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
        }
    }

    @Test
    void refusesHistoryWhenItsDeveloperChangesTheProcessorChoiceAfterRendering() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        var citation = mapper.createObjectNode()
                .put("artifactPath", "context/people/42/observations.jsonl")
                .put("startLine", 1)
                .put("endLine", 1);
        when(memberPolicy.allowsPerson(job, 42L)).thenReturn(false);
        assertThatThrownBy(() -> access(files).bind(job, citation, "a".repeat(64)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("processor");
    }

    @Test
    void malformedNumericSourceIdentityIsATypedAdmissionRefusal() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        for (String path : List.of("context/people/not-a-person/person.json", "repos/not-a-repo/.git/HEAD")) {
            assertThatThrownBy(() -> access(files)
                            .bind(job(), mapper.createObjectNode().put("artifactPath", path), "a".repeat(64)))
                    .isInstanceOf(JobDeliveryException.class)
                    .hasMessageContaining("source identity");
        }
    }

    @Test
    void refusesMissingCanonicalReferencesAndForeignRepositories() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var access = access(files);
        for (String path : List.of(
                "context/conversation_thread.json",
                "context/document.md",
                "inputs/history/feedback.json",
                "context/chat/C1/2026-10.jsonl",
                "context/docs/c/d.md",
                "context/people/42/person.json",
                "context/scm/2/pulls/1/record.json",
                "repos/2/.git/HEAD")) {
            assertThat(access.permits(
                            1L,
                            mapper.createObjectNode().put("artifactPath", path),
                            SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                    .isFalse();
        }
        var citation = mapper.createObjectNode().put("artifactPath", "repos/2/.git/HEAD");
        citation.putObject("sourceReference")
                .putArray("records")
                .addObject()
                .put("type", "repository")
                .put("id", 2L);
        when(repositories.permittedRepositories(1L)).thenReturn(List.of());
        assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"observations.jsonl", "feedback.jsonl", "person.json"})
    void canonicalPersonReferencesReuseVisibilityAndRecordWithdrawal(String filename) {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        String path = "context/people/42/" + filename;
        UUID recordId = UUID.randomUUID();
        byte[] bytes = ("{\"id\":\"" + recordId + "\",\"synced_at\":null}\n").getBytes(StandardCharsets.UTF_8);
        when(memberPolicy.allowsPerson(job, 42L)).thenReturn(true);
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        var member = new WorkspaceMembership();
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(member));
        String type = filename.startsWith("observations") ? "observation" : "feedback";
        when(history.permittedHistoryRecords(1L, type, Set.of(recordId), SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .thenReturn(Set.of(recordId));
        when(history.permittedHistoryRecords(1L, type, Set.of(recordId), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Set.of(recordId));
        try (var prepared = files.prepare(job, new PreparedEvidence(Map.of(path, bytes), null), null)) {
            assertThat(prepared.filesOnDisk()).containsKey(path);
            var citation = mapper.createObjectNode().put("artifactPath", path).put("startLine", 1);
            var access = access(files);
            access.bind(job, citation, ProvenanceDigest.sha256Hex(bytes));
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isTrue();
            if (!filename.equals("person.json")) {
                when(history.permittedHistoryRecords(
                                1L, type, Set.of(recordId), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                        .thenReturn(Set.of());
                assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                        .isFalse();
            }
            member.setHidden(true);
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            member.setHidden(false);
            when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.empty());
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
        }
    }

    @Test
    void canonicalDocumentRechecksItsSourceIdentityAndLocation() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var job = job();
        String path = "context/docs/engineering/design.md";
        byte[] bytes = "---\nsynced_at: null\nsource_id: \"source-7\"\n---\nquote\n".getBytes(StandardCharsets.UTF_8);
        var doc = mock(DocumentAccess.class);
        when(doc.sourceId()).thenReturn("source-7");
        when(doc.slug()).thenReturn("design");
        when(doc.collectionSlug()).thenReturn("engineering");
        when(doc.bodyAvailable()).thenReturn(true);
        when(documents.documentAccessByReference(Mockito.eq(1L), any())).thenReturn(List.of(doc));
        try (var prepared = files.prepare(job, new PreparedEvidence(Map.of(path, bytes), null), null)) {
            assertThat(prepared.filesOnDisk()).containsKey(path);
            var citation = mapper.createObjectNode().put("artifactPath", path);
            var access = access(files);
            access.bind(job, citation, ProvenanceDigest.sha256Hex(bytes));
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isTrue();
            when(doc.deleted()).thenReturn(true);
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            when(doc.deleted()).thenReturn(false);
            when(doc.bodyAvailable()).thenReturn(false);
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            when(doc.bodyAvailable()).thenReturn(true);
            when(doc.sourceId()).thenReturn("another-source");
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            when(doc.sourceId()).thenReturn("source-7");
            when(doc.slug()).thenReturn("renamed");
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            when(doc.slug()).thenReturn("design");
            when(doc.collectionSlug()).thenReturn("moved");
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isFalse();
            when(doc.collectionSlug()).thenReturn("engineering");
            assertThat(access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                    .isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "{}",
                "{\"records\":[{\"type\":\"person\"}]}",
                "{\"records\":[{\"type\":\"person\",\"person\":\"42\"}]}",
                "{\"records\":[{\"type\":\"repository\",\"id\":\"2\"}]}",
                "{\"records\":[{\"type\":\"repository\",\"id\":2.5}]}",
                "{\"records\":[{\"type\":\"chat\",\"channel\":null,\"message\":\"1\"}]}",
                "{\"records\":[{\"type\":\"observation\",\"id\":\"invalid\"}]}",
                "{\"records\":[]}",
                "{\"records\":[{\"type\":\"unknown\"}]}",
                "{\"records\":[{\"type\":\"observation\",\"person\":42,\"id\":\"invalid\"}]}"
            })
    void malformedPersistedReferencesFailClosed(String reference) {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        var citation = mapper.createObjectNode().put("artifactPath", "context/people/42/observations.jsonl");
        citation.set("sourceReference", mapper.readTree(reference));
        assertThat(access(files).permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isFalse();
    }

    @Test
    void shouldAnswerEveryCitationAsAloneWhenOneReadChecksThemAndReadEachSourceOnce() {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var access = access(files);
        when(repositories.permittedRepositories(1L)).thenReturn(List.of(TestEntities.repository(2L, "acme/api")));
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        var citations = List.of(
                citation("context/diff.patch", "{\"records\":[{\"type\":\"repository\",\"id\":2}]}"),
                citation("repos/2/src/Main.java", "{\"records\":[{\"type\":\"repository\",\"id\":2}]}"),
                citation("context/diff.patch", "{\"records\":[{\"type\":\"repository\",\"id\":3}]}"),
                citation("context/people/42/person.json", "{\"records\":[{\"type\":\"person\",\"person\":42}]}"),
                citation("context/people/42/person.json", "{\"records\":[{\"type\":\"person\",\"person\":42}]}"),
                citation(
                        "context/diff.patch",
                        "{\"records\":[{\"type\":\"repository\",\"id\":2},{\"type\":\"repository\",\"id\":3}]}"),
                citation(
                        "context/people/42/observations.jsonl",
                        "{\"records\":[{\"type\":\"observation\",\"person\":42,\"id\":\"invalid\"}]}"),
                mapper.createObjectNode().put("artifactPath", "context/diff.patch"));
        // Each citation alone, the way every citation was checked before one read shared its checks.
        List<Boolean> alone = citations.stream()
                .map(citation -> access.permits(1L, citation, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .toList();
        Mockito.clearInvocations(repositories, preferences, memberships);

        var checks = access.checks(1L, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        List<Boolean> together = citations.stream().map(checks::permits).toList();

        assertThat(together).isEqualTo(alone).containsExactly(true, true, false, true, true, false, false, true);
        Mockito.verify(repositories, Mockito.times(1)).permittedRepositories(1L);
        Mockito.verify(preferences, Mockito.times(1)).forDeveloper(1L, 42L);
        Mockito.verify(memberships, Mockito.times(1)).findByWorkspace_IdAndUser_Id(1L, 42L);
    }

    @Test
    void shouldBatchDirectSlackRecordsByExactChannelMessagePairsWhenPrepared() {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        var first = chat("C1", "1.000001");
        var second = chat("C1", "2.000002");
        var sameTimestampInAnotherChannel = chat("C2", "1.000001");
        when(conversations.readableMessages(
                        1L,
                        Set.of(
                                new MessageKey("C1", "1.000001"),
                                new MessageKey("C1", "2.000002"),
                                new MessageKey("C2", "1.000001"))))
                .thenReturn(Set.of(new MessageKey("C1", "1.000001"), new MessageKey("C2", "1.000001")));

        var checks = access.checks(1L, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        checks.prepare(List.of(first, second, sameTimestampInAnotherChannel));

        assertThat(List.of(first, second, sameTimestampInAnotherChannel).stream()
                        .map(checks::permits))
                .containsExactly(true, false, true);
        Mockito.verify(conversations, Mockito.times(1))
                .readableMessages(
                        1L,
                        Set.of(
                                new MessageKey("C1", "1.000001"),
                                new MessageKey("C1", "2.000002"),
                                new MessageKey("C2", "1.000001")));
        Mockito.verify(conversations, Mockito.never()).isMessageReadable(anyLong(), any(), any());
    }

    @Test
    void shouldPreserveRecordAndPersonGatesWhenPermittedSourcesShareAnIdentity() {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        var permitted = chat("C1", "1");
        var withPerson = chat("C1", "1");
        ((ObjectNode) withPerson.path("sourceReference").path("records").get(0)).put("person", 42L);
        var hiddenPerson = chat("C1", "1");
        ((ObjectNode) hiddenPerson.path("sourceReference").path("records").get(0)).put("person", 43L);
        var malformed = chat("C1", "1");
        ((ObjectNode) malformed.path("sourceReference").path("records").get(0)).put("message", 1);
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        when(preferences.forDeveloper(1L, 43L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        var hidden = new WorkspaceMembership();
        hidden.setHidden(true);
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 43L)).thenReturn(Optional.of(hidden));
        when(conversations.readableMessages(1L, Set.of(new MessageKey("C1", "1"))))
                .thenReturn(Set.of(new MessageKey("C1", "1")));

        var checks = access.checks(1L, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var citations = List.of(permitted, withPerson, hiddenPerson, malformed, permitted);
        checks.prepare(List.copyOf(citations));

        assertThat(citations.stream().map(checks::permits)).containsExactly(true, true, false, false, true);
        Mockito.verify(conversations, Mockito.times(1)).readableMessages(1L, Set.of(new MessageKey("C1", "1")));
    }

    @Test
    void shouldBatchOutlineRecordsWithBodyFreeAuthorityMetadataWhenPrepared() {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        var document = citation("context/document.md", "{\"records\":[{\"type\":\"document\",\"id\":7}]}");
        var evicted = citation("context/document.md", "{\"records\":[{\"type\":\"document\",\"id\":8}]}");
        var docsPath = citation(
                "context/docs/engineering/design.md",
                "{\"records\":[{\"type\":\"docs\",\"id\":\"source-7\",\"collection\":\"engineering\",\"slug\":\"design\"}]}");
        when(documents.documentAccessById(1L, Set.of(7L, 8L)))
                .thenReturn(List.of(
                        new DocumentAccess(7L, "source-7", "engineering", "design", false, true),
                        new DocumentAccess(8L, "source-8", "engineering", "evicted", false, false)));
        when(documents.documentAccessByReference(1L, Set.of("source-7")))
                .thenReturn(List.of(new DocumentAccess(7L, "source-7", "engineering", "design", false, true)));

        var checks = access.checks(1L, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        checks.prepare(List.of(document, evicted, docsPath));

        assertThat(List.of(document, evicted, docsPath).stream().map(checks::permits))
                .containsExactly(true, false, true);
        Mockito.verify(documents, Mockito.times(1)).documentAccessById(1L, Set.of(7L, 8L));
        Mockito.verify(documents, Mockito.times(1)).documentAccessByReference(1L, Set.of("source-7"));
        Mockito.verify(documents, Mockito.never()).documentById(anyLong(), anyLong());
        Mockito.verify(documents, Mockito.never()).documentsByReference(anyLong(), any());
    }

    /**
     * The golden comparison for history: a record of a developer's history is decided by authorizing the
     * observation it names, whose own citations may name older history in turn. Checked in batches, one per link
     * of the longest chain, every citation gets the answer it gets alone, where each record is checked on its own.
     */
    @ParameterizedTest
    @EnumSource(SourceUsePurpose.class)
    void shouldAnswerHistoryCitationsAsAloneWhenOneReadChecksTheirChainsInBatches(SourceUsePurpose purpose) {
        var files = new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies());
        var access = access(files);
        when(repositories.permittedRepositories(1L)).thenReturn(List.of(TestEntities.repository(2L, "acme/api")));
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        UUID newest = new UUID(0, 1);
        UUID middle = new UUID(0, 2);
        UUID oldest = new UUID(0, 3);
        UUID refused = new UUID(0, 4);
        UUID onRefused = new UUID(0, 5);
        UUID onHiddenRepository = new UUID(0, 6);
        UUID onWithdrawn = new UUID(0, 7);
        UUID missing = new UUID(0, 8);
        UUID onMissing = new UUID(0, 9);
        var document = citation("context/document.md", "{\"records\":[{\"type\":\"document\",\"id\":7}]}");
        var docs = citation(
                "context/docs/engineering/design.md",
                "{\"records\":[{\"type\":\"docs\",\"id\":\"source-7\",\"slug\":\"design\",\"collection\":\"engineering\"}]}");
        var metadata = new DocumentAccess(7L, "source-7", "engineering", "design", false, true);
        when(documents.documentAccessById(Mockito.eq(1L), any())).thenReturn(List.of(metadata));
        when(documents.documentAccessByReference(Mockito.eq(1L), any())).thenReturn(List.of(metadata));
        when(conversations.isMessageReadable(1L, "C1", "1")).thenReturn(true);
        when(conversations.isMessageReadable(1L, "C2", "2")).thenReturn(false);
        when(conversations.readableMessages(1L, Set.of(new MessageKey("C1", "1"), new MessageKey("C2", "2"))))
                .thenReturn(Set.of(new MessageKey("C1", "1")));
        // newest cites middle, which cites oldest; onRefused cites a record its owner refuses.
        Map<UUID, List<ObjectNode>> cites = Map.of(
                newest, List.of(history(middle)),
                middle, List.of(history(oldest), repository(2L), docs),
                oldest, List.of(repository(2L), chat("C1", "1"), document),
                refused, List.of(repository(2L)),
                onRefused, List.of(history(refused)),
                onHiddenRepository, List.of(repository(3L)),
                onWithdrawn, List.of(history(oldest), chat("C2", "2")),
                onMissing, List.of(history(missing)));
        // The history source as it works: a record is permitted when its owner keeps it and each citation of the
        // observation it names is permitted, checked with whatever checks the read has running.
        Predicate<UUID> permitted = id -> cites.containsKey(id)
                && !id.equals(refused)
                && cites.getOrDefault(id, List.of()).stream()
                        .allMatch(citation -> access.checks(1L, purpose).permits(citation));
        when(history.permitsHistoryRecord(
                        Mockito.eq(1L), Mockito.eq("observation"), Mockito.any(), Mockito.eq(purpose)))
                .thenAnswer(call -> permitted.test(call.getArgument(2)));
        when(history.historyRecordCitations(Mockito.eq(1L), Mockito.eq("observation"), Mockito.any()))
                .thenAnswer(call -> {
                    Map<UUID, List<JsonNode>> found = new HashMap<>();
                    for (UUID id : call.<Collection<UUID>>getArgument(2)) {
                        if (cites.containsKey(id)) found.put(id, List.copyOf(cites.get(id)));
                    }
                    return found;
                });
        when(history.permittedHistoryRecords(
                        Mockito.eq(1L), Mockito.eq("observation"), Mockito.any(), Mockito.eq(purpose)))
                .thenAnswer(call -> {
                    Collection<UUID> ids = call.getArgument(2);
                    // The batch authorizes its observations together, as the evidence authorization does.
                    var checks = access.checks(1L, purpose);
                    checks.prepare(ids.stream()
                            .flatMap(id -> cites.getOrDefault(id, List.of()).stream())
                            .map(JsonNode.class::cast)
                            .toList());
                    return ids.stream().filter(permitted).collect(Collectors.toSet());
                });
        List<ObjectNode> citations = List.of(
                history(newest),
                history(middle),
                history(onRefused),
                history(refused),
                history(onHiddenRepository),
                history(onWithdrawn),
                history(missing),
                history(onMissing),
                chat("C1", "1"),
                document,
                citation(
                        "context/people/42/observations.jsonl",
                        "{\"records\":[{\"type\":\"observation\",\"person\":42,\"id\":\"" + newest
                                + "\"},{\"type\":\"observation\",\"person\":42,\"id\":\"" + onRefused
                                + "\"}]}"));
        List<Boolean> alone = citations.stream()
                .map(citation -> access.checks(1L, purpose).permits(citation))
                .toList();
        Mockito.clearInvocations(history, conversations, documents);

        var checks = access.checks(1L, purpose);
        checks.prepare(List.copyOf(citations));
        List<Boolean> together = citations.stream().map(checks::permits).toList();

        assertThat(together)
                .isEqualTo(alone)
                .containsExactly(true, true, false, false, false, false, false, false, true, true, false);
        Mockito.verify(conversations, Mockito.times(1))
                .readableMessages(1L, Set.of(new MessageKey("C1", "1"), new MessageKey("C2", "2")));
        Mockito.verify(conversations, Mockito.never()).isMessageReadable(anyLong(), any(), any());
        Mockito.verify(documents, Mockito.times(1)).documentAccessById(1L, Set.of(7L));
        Mockito.verify(documents, Mockito.times(1)).documentAccessByReference(1L, Set.of("source-7"));
        // Three links in the longest chain, three batches; no record is checked alone.
        Mockito.verify(history, Mockito.times(3))
                .permittedHistoryRecords(Mockito.eq(1L), Mockito.eq("observation"), Mockito.any(), Mockito.eq(purpose));
        Mockito.verify(history, Mockito.never())
                .permitsHistoryRecord(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldStopHistoryReadsAt500RecordsWhenOperatorHistoryHasALongChain() {
        var purpose = SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW;
        Map<String, List<JsonNode>> cites = new HashMap<>();
        for (int i = 1; i <= 503; i++) {
            cites.put("observation:" + new UUID(0, i), i == 503 ? List.of() : List.of(history(new UUID(0, i + 1))));
        }
        var shortRoot = new UUID(0, 6000);
        var shortLeaf = new UUID(0, 6001);
        cites.put("observation:" + shortRoot, List.of(history(shortLeaf)));
        cites.put("observation:" + shortLeaf, List.of(repository(2L)));
        List<String> reads = new ArrayList<>();
        var access = historyAccess(purpose, cites, reads);
        var checks = access.checks(1L, purpose);
        var longRoot = history(new UUID(0, 1));
        var independent = history(shortRoot);
        var direct = repository(2L);

        checks.prepare(List.of(longRoot, independent, direct));

        Set<String> allowed = new HashSet<>();
        for (int i = 1; i <= 498; i++) allowed.add("observation:" + new UUID(0, i));
        allowed.add("observation:" + shortRoot);
        allowed.add("observation:" + shortLeaf);
        assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
        assertThat(checks.permits(longRoot)).isFalse();
        assertThat(checks.permits(independent)).isTrue();
        assertThat(checks.permits(direct)).isTrue();
        assertThat(checks.permits(history(new UUID(0, 499)))).isFalse();
        assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
    }

    @Test
    void shouldKeepRootsAndStableBreadthFirstLeavesWhenOperatorHistoryHasABroadBranch() {
        var purpose = SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW;
        var branch = new UUID(0, 1);
        var independent = new UUID(0, 6000);
        Map<String, List<JsonNode>> cites = new HashMap<>();
        List<JsonNode> leaves = new ArrayList<>();
        for (int i = 2; i <= 502; i++) {
            var id = new UUID(0, i);
            leaves.add(history(id));
            cites.put("observation:" + id, List.of());
        }
        cites.put("observation:" + branch, leaves);
        cites.put("observation:" + independent, List.of(repository(2L)));
        List<String> reads = new ArrayList<>();
        var access = historyAccess(purpose, cites, reads);
        var checks = access.checks(1L, purpose);

        checks.prepare(List.of(history(branch), history(independent), repository(2L)));

        Set<String> allowed = new HashSet<>();
        for (int i = 1; i <= 499; i++) allowed.add("observation:" + new UUID(0, i));
        allowed.add("observation:" + independent);
        assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
        assertThat(checks.permits(history(branch))).isFalse();
        assertThat(checks.permits(history(independent))).isTrue();
        assertThat(checks.permits(repository(2L))).isTrue();
        assertThat(checks.permits(history(new UUID(0, 499)))).isTrue();
        assertThat(checks.permits(history(new UUID(0, 500)))).isFalse();
        assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
    }

    @Test
    void shouldCountUniqueTypeAndIdBeforeDependenciesWhenOperatorRootsFillTheBudget() {
        var purpose = SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW;
        Map<String, List<JsonNode>> cites = new HashMap<>();
        List<JsonNode> roots = new ArrayList<>();
        Set<String> allowed = new HashSet<>();
        for (int i = 1; i <= 499; i++) {
            var id = new UUID(0, i);
            roots.add(history(id));
            cites.put("observation:" + id, List.of());
            if (i <= 498) allowed.add("observation:" + id);
        }
        var first = new UUID(0, 1);
        var duplicate = history(first);
        ((ObjectNode) duplicate.path("sourceReference").path("records").get(0)).put("synced_at", "2026-10-01");
        roots.add(1, duplicate);
        roots.add(history("feedback", first));
        allowed.add("feedback:" + first);
        cites.put("feedback:" + first, List.of());
        var deep = new UUID(0, 6000);
        cites.put("observation:" + first, List.of(history(deep)));
        cites.put("observation:" + deep, List.of());
        var last = new UUID(0, 499);
        roots.add(history("feedback", last));
        cites.put("feedback:" + last, List.of());
        allowed.add("feedback:" + last);

        for (var orderedRoots : List.of(roots, roots.reversed())) {
            List<String> reads = new ArrayList<>();
            var access = historyAccess(purpose, cites, reads);
            var checks = access.checks(1L, purpose);

            checks.prepare(orderedRoots);

            assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
            assertThat(checks.permits(history(new UUID(0, 498)))).isTrue();
            assertThat(checks.permits(history(last))).isFalse();
            assertThat(checks.permits(history("feedback", first))).isTrue();
            assertThat(checks.permits(history(first))).isFalse();
            assertThat(checks.permits(duplicate)).isFalse();
            assertThat(checks.permits(history("feedback", last))).isTrue();
            assertThat(checks.permits(history(deep))).isFalse();
            assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
        }
    }

    @Test
    void shouldKeepTheSameOperatorDependencyAllowanceWhenFeedbackCitationOrderChanges() {
        var purpose = SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW;
        var feedbackRoot = new UUID(0, 6000);
        var independentRoot = new UUID(0, 6001);
        var x = new UUID(0, 7000);
        var y = new UUID(0, 7001);
        Map<String, List<JsonNode>> cites = new HashMap<>();
        List<JsonNode> roots = new ArrayList<>();
        roots.add(history("feedback", feedbackRoot));
        roots.add(history(independentRoot));
        Set<String> allowed = new HashSet<>();
        allowed.add("feedback:" + feedbackRoot);
        allowed.add("observation:" + independentRoot);
        for (int i = 1; i <= 497; i++) {
            var id = new UUID(0, i);
            roots.add(history(id));
            cites.put("observation:" + id, List.of());
            allowed.add("observation:" + id);
        }
        cites.put("observation:" + independentRoot, List.of(history(x)));
        cites.put("observation:" + x, List.of());
        cites.put("observation:" + y, List.of());
        allowed.add("observation:" + x);

        for (boolean reversed : List.of(false, true)) {
            cites.put(
                    "feedback:" + feedbackRoot,
                    reversed ? List.of(history(y), history(x)) : List.of(history(x), history(y)));
            List<String> reads = new ArrayList<>();
            var access = historyAccess(purpose, cites, reads);
            var checks = access.checks(1L, purpose);

            checks.prepare(roots);

            assertThat(new HashSet<>(reads)).hasSize(500);
            assertThat(checks.permits(history(independentRoot)))
                    .as("Independent root with %s feedback citation order", reversed ? "reversed" : "forward")
                    .isTrue();
            assertThat(checks.permits(history("feedback", feedbackRoot))).isFalse();
            assertThat(checks.permits(history(x))).isTrue();
            assertThat(checks.permits(history(y))).isFalse();
            assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
        }
    }

    @Test
    void shouldShareTheOperatorAllowanceAcrossNestedPreparedAndScalarChecksWhenOneReadIsActive() {
        var purpose = SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW;
        Map<String, List<JsonNode>> cites = new HashMap<>();
        List<JsonNode> roots = new ArrayList<>();
        Set<String> allowed = new HashSet<>();
        for (int i = 1; i <= 501; i++) {
            var id = new UUID(0, i);
            cites.put("observation:" + id, List.of());
            if (i <= 499) {
                roots.add(history(id));
                allowed.add("observation:" + id);
            }
        }
        var sharedId = new UUID(0, 1);
        cites.put("feedback:" + sharedId, List.of(history(new UUID(0, 499))));
        allowed.add("feedback:" + sharedId);
        List<String> reads = new ArrayList<>();
        var access = historyAccess(purpose, cites, reads);

        access.asOneRead(1L, purpose, () -> {
            var checks = access.checks(1L, purpose);
            checks.prepare(roots);
            assertThat(access.asOneRead(1L, purpose, () -> {
                        var nested = access.checks(1L, purpose);
                        assertThat(nested).isSameAs(checks);
                        return nested.permits(history("feedback", sharedId));
                    }))
                    .isTrue();
            assertThat(access.permits(1L, history(new UUID(0, 500)), purpose)).isFalse();
            assertThat(checks.permits(history(new UUID(0, 501)))).isFalse();
            checks.prepare(List.of(history(sharedId)));
            assertThat(checks.permits(history(sharedId))).isTrue();
            assertThat(new HashSet<>(reads)).containsExactlyInAnyOrderElementsOf(allowed);
            return true;
        });

        assertThat(access.permits(1L, history(new UUID(0, 500)), purpose)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SourceUsePurpose.class, mode = EnumSource.Mode.EXCLUDE, names = "OPERATOR_EVIDENCE_REVIEW")
    void shouldPermitHistoryBeyond500RecordsWhenThePurposeIsNotOperatorReview(SourceUsePurpose purpose) {
        Map<String, List<JsonNode>> cites = new HashMap<>();
        for (int i = 1; i <= 501; i++) {
            cites.put("observation:" + new UUID(0, i), i == 501 ? List.of() : List.of(history(new UUID(0, i + 1))));
        }
        List<String> reads = new ArrayList<>();
        var access = historyAccess(purpose, cites, reads);
        var checks = access.checks(1L, purpose);
        var citation = history(new UUID(0, 1));

        checks.prepare(List.of(citation));

        assertThat(checks.permits(citation)).isTrue();
        assertThat(new HashSet<>(reads)).hasSize(501).contains("observation:" + new UUID(0, 501));
    }

    @Test
    void shouldRecheckRepositoryAccessAfterEachReadIncludingAFailedRead() {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        var purpose = SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY;
        when(repositories.permittedRepositories(1L)).thenReturn(List.of(TestEntities.repository(2L, "acme/api")));
        assertThatThrownBy(() -> access.asOneRead(1L, purpose, () -> {
                    assertThat(access.permits(1L, repository(2L), purpose)).isTrue();
                    throw new IllegalStateException("Read failed");
                }))
                .isInstanceOf(IllegalStateException.class);
        when(repositories.permittedRepositories(1L)).thenReturn(List.of());
        assertThat(access.asOneRead(1L, purpose, () -> access.permits(1L, repository(2L), purpose)))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = SourceUsePurpose.class,
            names = {"PRACTICE_FEEDBACK_DELIVERY", "OPERATOR_EVIDENCE_REVIEW"})
    void shouldKeepNestedWorkspacesPurposesAndThreadsSeparate(SourceUsePurpose purpose) throws Exception {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        when(repositories.permittedRepositories(1L)).thenReturn(List.of(TestEntities.repository(2L, "acme/api")));
        when(repositories.permittedRepositories(2L)).thenReturn(List.of());
        access.asOneRead(1L, purpose, () -> {
            var outer = access.checks(1L, purpose);
            assertThat(outer.permits(repository(2L))).isTrue();
            assertThat(access.asOneRead(2L, purpose, () -> access.permits(2L, repository(2L), purpose)))
                    .isFalse();
            assertThat(access.checks(1L, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                    .isNotSameAs(outer);
            assertThat(access.checks(1L, purpose)).isSameAs(outer);
            try (var executor = Executors.newSingleThreadExecutor()) {
                assertThat(executor.submit(() -> access.checks(1L, purpose)).get())
                        .isNotSameAs(outer);
                assertThatThrownBy(() -> executor.submit(() -> outer.permits(repository(2L)))
                                .get())
                        .hasCauseInstanceOf(IllegalStateException.class);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            return true;
        });
    }

    @Test
    void shouldNotReuseAnAnswerForADifferentReader() {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        var purpose = SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY;
        try {
            CurrentScmIdentityHolder.set(42L, "first", Set.of(42L));
            access.asOneRead(1L, purpose, () -> {
                var first = access.checks(1L, purpose);
                CurrentScmIdentityHolder.set(43L, "second", Set.of(43L));
                assertThat(access.checks(1L, purpose)).isNotSameAs(first);
                assertThatThrownBy(() -> first.permits(repository(2L))).isInstanceOf(IllegalStateException.class);
                return true;
            });
        } finally {
            CurrentScmIdentityHolder.clear();
        }
    }

    @ParameterizedTest
    @EnumSource(MemberAiChoice.class)
    void shouldApplyEveryAiChoiceWithoutChangingTheDeliveryPurpose(MemberAiChoice choice) {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(true, choice));
        var person = citation("context/people/42/person.json", "{\"records\":[{\"type\":\"person\",\"person\":42}]}");
        assertThat(access.permits(1L, person, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isEqualTo(choice != MemberAiChoice.NO_AI);
    }

    @Test
    void shouldRefuseHiddenOptedOutAndErasedPeopleOnTheNextRead() {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        var purpose = SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY;
        var person = citation("context/people/42/person.json", "{\"records\":[{\"type\":\"person\",\"person\":42}]}");
        var member = new WorkspaceMembership();
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(member));
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        assertThat(access.permits(1L, person, purpose)).isTrue();
        member.setHidden(true);
        assertThat(access.permits(1L, person, purpose)).isFalse();
        member.setHidden(false);
        when(preferences.forDeveloper(1L, 42L))
                .thenReturn(new MemberAiPreferences.Decision(true, MemberAiChoice.NO_AI));
        assertThat(access.permits(1L, person, purpose)).isFalse();
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(suppression.isUserSuppressed(42L)).thenReturn(true);
        assertThat(access.permits(1L, person, purpose)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(SourceUsePurpose.class)
    void shouldDenyHistoryCyclesAndTheirDependentsWithoutSingleRecordReads(SourceUsePurpose purpose) {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        UUID dependent = new UUID(0, 3);
        when(history.historyRecordCitations(Mockito.eq(1L), Mockito.eq("observation"), Mockito.any()))
                .thenAnswer(call -> {
                    Map<UUID, List<JsonNode>> found = new HashMap<>();
                    for (UUID id : call.<Collection<UUID>>getArgument(2)) {
                        found.put(id, List.of(history(id.equals(first) ? second : first)));
                    }
                    return found;
                });
        var checks = access.checks(1L, purpose);
        checks.prepare(List.of(history(dependent), history(first), history(second)));
        assertThat(List.of(dependent, first, second).stream().map(id -> checks.permits(history(id))))
                .containsOnly(false);
        Mockito.verify(history, Mockito.never())
                .permitsHistoryRecord(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(history, Mockito.never())
                .permittedHistoryRecords(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private CitedSourceAccess historyAccess(
            SourceUsePurpose purpose, Map<String, List<JsonNode>> cites, List<String> reads) {
        var access = access(new JobEvidenceFiles(
                new FabricLayout(root.toString()), mock(AgentJobRepository.class), Clock.systemUTC(), personCopies()));
        when(repositories.permittedRepositories(1L)).thenReturn(List.of(TestEntities.repository(2L, "acme/api")));
        when(preferences.forDeveloper(1L, 42L)).thenReturn(new MemberAiPreferences.Decision(false, null));
        when(memberships.findByWorkspace_IdAndUser_Id(1L, 42L)).thenReturn(Optional.of(new WorkspaceMembership()));
        when(history.historyRecordCitations(Mockito.eq(1L), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> {
                    String type = call.getArgument(1);
                    Map<UUID, List<JsonNode>> found = new HashMap<>();
                    for (UUID id : call.<Collection<UUID>>getArgument(2)) {
                        String key = type + ":" + id;
                        reads.add(key);
                        List<JsonNode> citations = cites.get(key);
                        if (citations != null) found.put(id, citations);
                    }
                    return found;
                });
        // The source decides row existence and checks child citations through the active production checks.
        when(history.permitsHistoryRecord(Mockito.eq(1L), Mockito.any(), Mockito.any(), Mockito.eq(purpose)))
                .thenAnswer(call -> {
                    String key = call.<String>getArgument(1) + ":" + call.<UUID>getArgument(2);
                    reads.add(key);
                    return cites.containsKey(key)
                            && cites.getOrDefault(key, List.of()).stream()
                                    .allMatch(citation ->
                                            access.checks(1L, purpose).permits(citation));
                });
        when(history.permittedHistoryRecords(Mockito.eq(1L), Mockito.any(), Mockito.any(), Mockito.eq(purpose)))
                .thenAnswer(call -> {
                    String type = call.getArgument(1);
                    Collection<UUID> ids = call.getArgument(2);
                    var checks = access.checks(1L, purpose);
                    checks.prepare(ids.stream()
                            .flatMap(id -> cites.getOrDefault(type + ":" + id, List.of()).stream())
                            .toList());
                    return ids.stream()
                            .filter(id -> {
                                String key = type + ":" + id;
                                reads.add(key);
                                return cites.containsKey(key)
                                        && cites.getOrDefault(key, List.of()).stream()
                                                .allMatch(checks::permits);
                            })
                            .collect(Collectors.toSet());
                });
        return access;
    }

    private ObjectNode history(UUID id) {
        return history("observation", id);
    }

    private ObjectNode history(String type, UUID id) {
        return citation(
                "context/people/42/" + (type.equals("observation") ? "observations" : "feedback") + ".jsonl",
                "{\"records\":[{\"type\":\"" + type + "\",\"person\":42,\"id\":\"" + id + "\"}]}");
    }

    private ObjectNode repository(long id) {
        return citation("context/diff.patch", "{\"records\":[{\"type\":\"repository\",\"id\":" + id + "}]}");
    }

    private ObjectNode chat(String channel, String message) {
        return citation(
                "context/chat/" + channel + "/2026-10.jsonl",
                "{\"records\":[{\"type\":\"chat\",\"channel\":\"" + channel + "\",\"message\":\"" + message + "\"}]}");
    }

    private ObjectNode citation(String path, String reference) {
        var citation = mapper.createObjectNode().put("artifactPath", path);
        citation.set("sourceReference", mapper.readTree(reference));
        return citation;
    }
}
