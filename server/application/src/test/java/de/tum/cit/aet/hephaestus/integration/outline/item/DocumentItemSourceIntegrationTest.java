package de.tum.cit.aet.hephaestus.integration.outline.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentContentSource;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentEvents;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import de.tum.cit.aet.hephaestus.workspace.MembershipService;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * A document sits in its collection, and a reader is told which one by the name Outline shows — never by the
 * collection's url id, a random slug that means nothing to anybody.
 */
@Transactional
@TestPropertySource(
        properties = {
            "hephaestus.integration.outline.enabled=true",
            "hephaestus.integration.outline.allowed-origins=https://wiki.example.com"
        })
class DocumentItemSourceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DocumentItemSource resolver;

    @Autowired
    private OutlineDocumentRepository documents;

    @Autowired
    private OutlineCollectionRepository collections;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private IdentityRepository identities;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private ItemWriter items;

    @Autowired
    private MembershipService memberships;

    @Test
    void aContextualCollaboratorsAccountChoiceWithholdsTheWholePinnedDocument() {
        var fixture = readableDocument();
        assertThat(resolver.permits(fixture.item(), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isTrue();
        var pinned = resolver.pin(fixture.item(), null);
        var captured = resolver.capture(pinned, request());
        assertThat(new String(
                        Objects.requireNonNull(captured.files().get(DocumentContentSource.BODY_KEY)),
                        StandardCharsets.UTF_8))
                .isEqualTo("Complete document body");
        assertThat(pinned.roles())
                .singleElement()
                .satisfies(role -> assertThat(role.identity().teamId()).isEqualTo(fixture.teamId()));
        var collaborator = accounts.findById(fixture.collaboratorId()).orElseThrow();
        collaborator.setAiChoice(AiChoice.NO_AI);
        collaborator.setAiChoiceAt(Instant.now());
        accounts.saveAndFlush(collaborator);
        assertThat(resolver.permits(fixture.item(), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .isFalse();
        var withheld = resolver.capture(pinned, request());
        assertThat(withheld.files()).isEmpty();
        assertThat(withheld.completeness()).isEmpty();
        assertThat(withheld.stateOverrides())
                .containsEntry(
                        DocumentContentSource.SOURCE,
                        new SourceCaptureState.Redacted(SourceAbsenceReason.ACCESS_NOT_PERMITTED));
    }

    @Test
    void theSameNativeSubjectInAnotherTeamDoesNotOwnOrSuppressThisDocument() {
        var fixture = readableDocument();
        var foreign = accounts.save(new Account("Other team collaborator"));
        foreign.setAiChoice(AiChoice.NO_AI);
        foreign.setAiChoiceAt(Instant.now());
        accounts.saveAndFlush(foreign);
        var nativeIdentity = new Identity();
        nativeIdentity.setAccount(foreign);
        nativeIdentity.setProviderId(fixture.providerId());
        nativeIdentity.setProviderUserKey("collaborator");
        nativeIdentity.setTeamKey("other-" + fixture.teamId());
        nativeIdentity.setLinkedVia(Identity.LinkedVia.ROSTER);
        identities.saveAndFlush(nativeIdentity);
        assertThat(resolver.permits(fixture.item(), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .isTrue();
        var captured = resolver.capture(resolver.pin(fixture.item(), null), request());
        assertThat(captured.files()).containsKey(DocumentContentSource.BODY_KEY);
        long foreignWorkspace = workspaces
                .save(WorkspaceTestFixtures.activeWorkspace("doc-foreign-" + UUID.randomUUID()))
                .getId();
        assertThat(resolver.current(new ItemRef(
                        foreignWorkspace,
                        fixture.item().itemId(),
                        DocumentEvents.DOCUMENT,
                        fixture.item().sourceId(),
                        null,
                        null)))
                .isEmpty();
    }

    private ReadableDocument readableDocument() {
        var workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace("doc-source-" + UUID.randomUUID()));
        String team = UUID.randomUUID().toString();
        var connection = new Connection(
                workspace,
                IntegrationKind.OUTLINE,
                team,
                new ConnectionConfig.OutlineConfig("https://wiki.example.com", null, null, Set.of()));
        connection.setState(IntegrationState.ACTIVE);
        connections.saveAndFlush(connection);
        var provider = providers
                .findByTypeAndServerUrl(IdentityProviderType.OUTLINE, "https://wiki.example.com")
                .orElseGet(() ->
                        providers.save(new IdentityProvider(IdentityProviderType.OUTLINE, "https://wiki.example.com")));
        human(workspace, provider.getId(), team, "creator");
        long collaborator = human(workspace, provider.getId(), team, "collaborator");
        var document = new OutlineDocument();
        document.setWorkspaceId(workspace.getId());
        document.setConnectionId(connection.getId());
        document.setDocumentId("document");
        document.setSlug("document");
        document.setTitle("Complete document");
        document.setCollectionId("collection");
        document.setBodyMarkdown("Complete document body");
        document.setContentHash("full-body-hash");
        document.setCreatedBySubject("creator");
        document.setCreatedByName("Creator");
        document.setCollaboratorSubjects(List.of("collaborator"));
        documents.saveAndFlush(document);
        var item = items.upsert(
                new ItemRegistration(workspace.getId(), DocumentEvents.DOCUMENT, document.getId(), null, null));
        return new ReadableDocument(item, collaborator, provider.getId(), team);
    }

    private long human(Workspace workspace, long providerId, String team, String subject) {
        var account = accounts.save(new Account("Human " + subject));
        var identity = new Identity();
        identity.setAccount(account);
        identity.setProviderId(providerId);
        identity.setProviderUserKey(subject);
        identity.setTeamKey(team);
        identity.setLinkedVia(Identity.LinkedVia.ROSTER);
        identities.saveAndFlush(identity);
        memberships.createMembership(workspace, account.getId(), Membership.WorkspaceRole.MEMBER);
        return account.getId();
    }

    private static CaptureRequest request() {
        return new CaptureRequest(
                UUID.randomUUID(), UUID.randomUUID(), 1, Set.of(DocumentContentSource.SOURCE), "2.0.0", 3);
    }

    private record ReadableDocument(ItemRef item, long collaboratorId, long providerId, String teamId) {}

    @Test
    void shouldNameADocumentsCollectionByItsNameAndNeverByItsUrlId() {
        long workspaceId = workspaces
                .save(WorkspaceTestFixtures.activeWorkspace("document-identity"))
                .getId();
        collection(workspaceId, "col-named", "Engineering", "hK3pQ9xZ2a");
        collection(workspaceId, "col-unnamed", null, "Zq81mNw3Lk");
        OutlineDocument named = document(workspaceId, "doc-named", "col-named", "hK3pQ9xZ2a", "Deployment runbook");
        OutlineDocument unnamed = document(workspaceId, "doc-unnamed", "col-unnamed", "Zq81mNw3Lk", " ");
        OutlineDocument orphaned = document(workspaceId, "doc-orphaned", "col-gone", "Pp09aaBbCc", "Old notes");

        Map<Long, ItemLabel> resolved = resolver.labels(
                workspaceId,
                List.of(item(workspaceId, named), item(workspaceId, unnamed), item(workspaceId, orphaned)));

        ItemLabel inNamedCollection = Objects.requireNonNull(resolved.get(named.getId()));
        assertThat(inNamedCollection.title()).isEqualTo("Deployment runbook");
        assertThat(inNamedCollection.container()).isEqualTo("Engineering");

        ItemLabel inUnnamedCollection = Objects.requireNonNull(resolved.get(unnamed.getId()));
        assertThat(inUnnamedCollection.title()).isEqualTo("Document");
        assertThat(inUnnamedCollection.container())
                .as("a collection with no captured name")
                .isNull();

        assertThat(Objects.requireNonNull(resolved.get(orphaned.getId())).container())
                .as("a collection the mirror no longer holds")
                .isNull();
    }

    private static ItemRef item(long workspaceId, OutlineDocument document) {
        return new ItemRef(workspaceId, document.getId(), DocumentEvents.DOCUMENT, document.getId(), null, null);
    }

    private void collection(long workspaceId, String collectionId, @Nullable String name, String urlId) {
        OutlineCollection collection = new OutlineCollection();
        collection.setWorkspaceId(workspaceId);
        collection.setConnectionId(1L);
        collection.setCollectionId(collectionId);
        collection.setName(name);
        collection.setUrlId(urlId);
        collections.save(collection);
    }

    private OutlineDocument document(
            long workspaceId, String documentId, String collectionId, String collectionSlug, String title) {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(workspaceId);
        doc.setConnectionId(1L);
        doc.setDocumentId(documentId);
        doc.setCollectionId(collectionId);
        doc.setCollectionSlug(collectionSlug);
        doc.setSlug(documentId);
        doc.setTitle(title);
        return documents.save(doc);
    }
}
