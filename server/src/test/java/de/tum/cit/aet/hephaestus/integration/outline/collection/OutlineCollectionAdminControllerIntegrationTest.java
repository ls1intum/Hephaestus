package de.tum.cit.aet.hephaestus.integration.outline.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiException;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.connect.OutlineConnectionStatusDTO;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end tests for {@link OutlineCollectionAdminController} and
 * {@link de.tum.cit.aet.hephaestus.integration.outline.connect.OutlineConnectionAdminController} —
 * the Outline admin surface. The whole Outline integration is enabled so the guarded controllers +
 * services run against a real Postgres via {@link WebTestClient}; {@link OutlineApiClient} is mocked
 * so live verification, candidates, and the registration sync kick are observable without an Outline
 * server.
 *
 * <p>Each test asserts a behavior that would fail if the guard or contract were removed: a non-admin
 * is 403; a workspace without an ACTIVE Outline connection is 404; register verifies against the live
 * list (unknown → 422, known → 201, repeat → 200) and round-trips through the list; PATCH resumes
 * reset the sync status to PENDING; DELETE erases the mirrored documents; an Outline API failure on
 * the candidates probe is a 502 ProblemDetail; and the connection status endpoint reports webhook /
 * count / last-sync truthfully.
 */
@TestPropertySource(properties = "hephaestus.integration.outline.enabled=true")
class OutlineCollectionAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String SERVER_URL = "https://outline.example.test";
    private static final String COLLECTION_ID = "col-1";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private OutlineCollectionRepository collectionRepository;

    @Autowired
    private OutlineDocumentRepository documentRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private CredentialBundleConverter credentialConverter;

    @MockitoBean
    private OutlineApiClient outlineApiClient;

    private Workspace workspace;
    private long connectionId;

    @BeforeEach
    void setUp() {
        User owner = persistUser("outline-admin-owner-" + System.nanoTime());
        workspace = createWorkspace(
            "outline-admin-" + System.nanoTime(),
            "Outline Admin Test",
            "outline-admin-org",
            AccountType.ORG,
            owner
        );
        connectionId = seedActiveOutlineConnection(workspace);

        // The registration kick runs the real sync service against the mocked client off the request
        // thread; benign empty stubs keep that background pass a harmless no-op. The candidates path
        // uses the bounded (maxPages) overload, register/sync the plain one — stub both.
        lenient()
            .when(outlineApiClient.listCollections(anyString(), anyString()))
            .thenReturn(
                List.of(new OutlineCollectionListResponse.Collection(COLLECTION_ID, "Design", "col1", null, null))
            );
        lenient()
            .when(outlineApiClient.listCollections(anyString(), anyString(), anyInt()))
            .thenReturn(
                List.of(new OutlineCollectionListResponse.Collection(COLLECTION_ID, "Design", "col1", null, null))
            );
        lenient().when(outlineApiClient.listDocuments(anyString(), anyString(), anyString())).thenReturn(List.of());
        lenient()
            .when(outlineApiClient.listCollectionDocuments(anyString(), anyString(), anyString()))
            .thenReturn(List.of());
    }

    @Test
    @WithMentorUser
    @DisplayName("non-admin cannot reach the collection control plane → 403")
    void nonAdmin_forbidden() {
        User mentor = persistUser("mentor");
        ensureWorkspaceMembership(workspace, mentor, WorkspaceRole.MEMBER);

        listRequest().expectStatus().isForbidden();
        registerRequest(COLLECTION_ID).expectStatus().isForbidden();
    }

    @Test
    @WithAdminUser
    @DisplayName("workspace without an ACTIVE Outline connection → 404 ProblemDetail everywhere")
    void withoutConnection_notFound() {
        ensureAdminMembership(workspace);
        connectionRepository.deleteById(connectionId);

        listRequest().expectStatus().isNotFound();
        registerRequest(COLLECTION_ID).expectStatus().isNotFound();
        webTestClient
            .get()
            .uri("/workspaces/{slug}/outline/collections/{id}", workspace.getWorkspaceSlug(), COLLECTION_ID)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
        webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/outline/status", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
        webTestClient
            .post()
            .uri("/workspaces/{slug}/connections/outline/sync", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    @DisplayName("register verifies the id live, lands ENABLED+PENDING, round-trips through list, repeat is 200")
    void registerAndListRoundTrip() {
        ensureAdminMembership(workspace);

        OutlineCollectionDTO created = registerRequest(COLLECTION_ID)
            .expectStatus()
            .isCreated()
            // 201 points at the freshly minted item resource.
            .expectHeader()
            .location("/workspaces/" + workspace.getWorkspaceSlug() + "/outline/collections/" + COLLECTION_ID)
            .expectBody(OutlineCollectionDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.collectionId()).isEqualTo(COLLECTION_ID);
        assertThat(created.name()).isEqualTo("Design");
        assertThat(created.state()).isEqualTo(MirrorState.ENABLED);
        assertThat(created.syncStatus()).isEqualTo(SyncStatus.PENDING);

        List<OutlineCollectionDTO> listed = listRequest()
            .expectStatus()
            .isOk()
            .expectBodyList(OutlineCollectionDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).collectionId()).isEqualTo(COLLECTION_ID);

        // Idempotent repeat: same natural key answers 200, not a duplicate row.
        registerRequest(COLLECTION_ID).expectStatus().isOk();
        assertThat(collectionRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspace.getId())).hasSize(1);
    }

    @Test
    @WithAdminUser
    @DisplayName("item GET returns one mirrored collection; an unregistered id is 404")
    void getSingleCollection() {
        ensureAdminMembership(workspace);
        seedCollection(MirrorState.ENABLED, SyncStatus.COMPLETE);
        seedDocument("doc-1");

        OutlineCollectionDTO fetched = webTestClient
            .get()
            .uri("/workspaces/{slug}/outline/collections/{id}", workspace.getWorkspaceSlug(), COLLECTION_ID)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(OutlineCollectionDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(fetched).isNotNull();
        assertThat(fetched.collectionId()).isEqualTo(COLLECTION_ID);
        assertThat(fetched.documentCount()).isEqualTo(1L);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/outline/collections/{id}", workspace.getWorkspaceSlug(), "no-such-collection")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    @DisplayName("register with an id the live list does not contain → 422 with a stable problem type")
    void registerUnknownCollection_unprocessable() {
        ensureAdminMembership(workspace);

        registerRequest("no-such-collection")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/problems/unknown-outline-collection");
        assertThat(collectionRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspace.getId())).isEmpty();
    }

    @Test
    @WithAdminUser
    @DisplayName("PATCH pauses and resumes; resume resets the sync status to PENDING")
    void patchState_pauseAndResume() {
        ensureAdminMembership(workspace);
        seedCollection(MirrorState.ENABLED, SyncStatus.COMPLETE);

        OutlineCollectionDTO paused = patchState(COLLECTION_ID, MirrorState.PAUSED)
            .expectStatus()
            .isOk()
            .expectBody(OutlineCollectionDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(paused).isNotNull();
        assertThat(paused.state()).isEqualTo(MirrorState.PAUSED);
        assertThat(paused.syncStatus()).isEqualTo(SyncStatus.COMPLETE);

        OutlineCollectionDTO resumed = patchState(COLLECTION_ID, MirrorState.ENABLED)
            .expectStatus()
            .isOk()
            .expectBody(OutlineCollectionDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(resumed).isNotNull();
        assertThat(resumed.state()).isEqualTo(MirrorState.ENABLED);
        // The frozen mirror drifted while paused — the resume forces the catch-up tick to reconverge.
        assertThat(resumed.syncStatus()).isEqualTo(SyncStatus.PENDING);

        OutlineCollection stored = collectionRepository
            .findByWorkspaceIdAndConnectionIdAndCollectionId(workspace.getId(), connectionId, COLLECTION_ID)
            .orElseThrow();
        assertThat(stored.getState()).isEqualTo(MirrorState.ENABLED);
        assertThat(stored.getSyncStatus()).isEqualTo(SyncStatus.PENDING);

        patchState("no-such-collection", MirrorState.PAUSED).expectStatus().isNotFound();
    }

    @Test
    @WithAdminUser
    @DisplayName("DELETE removes the registry row and hard-deletes the collection's mirrored documents")
    void deleteErasesMirroredDocuments() {
        ensureAdminMembership(workspace);
        seedCollection(MirrorState.ENABLED, SyncStatus.COMPLETE);
        seedDocument("doc-1");
        seedDocument("doc-2");

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/outline/collections/{id}", workspace.getWorkspaceSlug(), COLLECTION_ID)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        assertThat(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(
                workspace.getId(),
                connectionId,
                COLLECTION_ID
            )
        ).isEmpty();
        // Erase is the point: the mirrored bodies left the database, no tombstones remain.
        assertThat(documentRepository.findByWorkspaceIdAndConnectionId(workspace.getId(), connectionId)).isEmpty();
    }

    @Test
    @WithAdminUser
    @DisplayName("candidates flag mirrored collections; an Outline API failure surfaces as 502 ProblemDetail")
    void candidates_flagAndUpstreamFailure() {
        ensureAdminMembership(workspace);
        seedCollection(MirrorState.ENABLED, SyncStatus.COMPLETE);

        List<OutlineCollectionCandidateDTO> candidates = webTestClient
            .get()
            .uri("/workspaces/{slug}/outline/collections/candidates", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            // The live upstream view must never be cached by the browser or an intermediary.
            .expectHeader()
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .expectBodyList(OutlineCollectionCandidateDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).alreadyMirrored()).isTrue();

        when(outlineApiClient.listCollections(anyString(), anyString(), anyInt())).thenThrow(
            new OutlineApiException("Could not reach the Outline server")
        );
        webTestClient
            .get()
            .uri("/workspaces/{slug}/outline/collections/candidates", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isEqualTo(502)
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("The Outline server could not be reached")
            .jsonPath("$.type")
            .isEqualTo("/problems/outline-unreachable");
    }

    @Test
    @WithAdminUser
    @DisplayName("connection status reports webhook registration, live document count, and last clean pass")
    void connectionStatusShape() {
        ensureAdminMembership(workspace);
        seedCollection(MirrorState.ENABLED, SyncStatus.COMPLETE);
        seedDocument("doc-live");
        OutlineDocument tombstoned = seedDocument("doc-gone");
        tombstoned.setDeletedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        documentRepository.save(tombstoned);

        OutlineConnectionStatusDTO status = webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/outline/status", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(OutlineConnectionStatusDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(status).isNotNull();
        // The seeded connection has no webhook subscription id → not registered.
        assertThat(status.webhookRegistered()).isFalse();
        // Only the live row counts; the tombstone is excluded.
        assertThat(status.documentCount()).isEqualTo(1L);
        // No collection has finished a clean pass yet.
        assertThat(status.lastSyncedAt()).isNull();
        // No manual reconcile has been triggered, the seeded collection is ENABLED+COMPLETE, no error.
        assertThat(status.syncRunning()).isFalse();
        assertThat(status.pendingCollections()).isZero();
        assertThat(status.erroredCollections()).isZero();

        // A pending collection and a sync error show up in the derived counters.
        OutlineCollection stored = collectionRepository
            .findByWorkspaceIdAndConnectionIdAndCollectionId(workspace.getId(), connectionId, COLLECTION_ID)
            .orElseThrow();
        stored.setSyncStatus(SyncStatus.PENDING);
        stored.setLastSyncError("Outline /api/documents.export failed (HTTP 500)");
        collectionRepository.save(stored);

        OutlineConnectionStatusDTO withPending = webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/outline/status", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(OutlineConnectionStatusDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(withPending).isNotNull();
        assertThat(withPending.pendingCollections()).isEqualTo(1L);
        assertThat(withPending.erroredCollections()).isEqualTo(1L);

        // The manual sync always answers 202 and points at the status monitor above.
        webTestClient
            .post()
            .uri("/workspaces/{slug}/connections/outline/sync", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isAccepted()
            .expectHeader()
            .location("/workspaces/" + workspace.getWorkspaceSlug() + "/connections/outline/status");
    }

    // --- helpers ---

    private WebTestClient.ResponseSpec listRequest() {
        return webTestClient
            .get()
            .uri("/workspaces/{slug}/outline/collections", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange();
    }

    private WebTestClient.ResponseSpec registerRequest(String collectionId) {
        return webTestClient
            .post()
            .uri("/workspaces/{slug}/outline/collections", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new RegisterOutlineCollectionRequestDTO(collectionId))
            .exchange();
    }

    private WebTestClient.ResponseSpec patchState(String collectionId, MirrorState state) {
        return webTestClient
            .patch()
            .uri("/workspaces/{slug}/outline/collections/{id}", workspace.getWorkspaceSlug(), collectionId)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateOutlineCollectionStateRequestDTO(state))
            .exchange();
    }

    private long seedActiveOutlineConnection(Workspace ws) {
        Connection connection = new Connection(
            ws,
            IntegrationKind.OUTLINE,
            "team-1",
            new ConnectionConfig.OutlineConfig(SERVER_URL, null, null, Set.of())
        );
        connection.setCredentials(new BearerToken("outline-token", null), credentialConverter);
        connection.setState(IntegrationState.ACTIVE);
        return connectionRepository.save(connection).getId();
    }

    private void seedCollection(MirrorState state, SyncStatus syncStatus) {
        OutlineCollection collection = new OutlineCollection();
        collection.setWorkspaceId(workspace.getId());
        collection.setConnectionId(connectionId);
        collection.setCollectionId(COLLECTION_ID);
        collection.setName("Design");
        collection.setState(state);
        collection.setSyncStatus(syncStatus);
        collectionRepository.save(collection);
    }

    private OutlineDocument seedDocument(String documentId) {
        OutlineDocument document = new OutlineDocument();
        document.setWorkspaceId(workspace.getId());
        document.setConnectionId(connectionId);
        document.setCollectionId(COLLECTION_ID);
        document.setDocumentId(documentId);
        document.setTitle(documentId);
        document.setBodyMarkdown("# " + documentId);
        return documentRepository.save(document);
    }
}
