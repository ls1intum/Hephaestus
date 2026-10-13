package de.tum.cit.aet.hephaestus.integration.outline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.SqlReadMeasurement;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

class OutlineDocumentAccessIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private OutlineDocumentRepository documents;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private SqlReadMeasurement reads;

    @Autowired
    private JdbcTemplate jdbc;

    private Workspace workspace;
    private Connection connection;

    @BeforeEach
    void createWorkspaceAndConnection() {
        workspace = workspaces.save(WorkspaceTestFixtures.activeWorkspace("outline-access-" + UUID.randomUUID()));
        connection = connect("https://outline-access.example.com");
    }

    private Connection connect(String origin) {
        var created = new Connection(
                workspace,
                IntegrationKind.OUTLINE,
                UUID.randomUUID().toString(),
                new ConnectionConfig.OutlineConfig(origin, null, null, Set.of()));
        created.setState(IntegrationState.ACTIVE);
        return connections.saveAndFlush(created);
    }

    private OutlineDocument document(String sourceId, Connection owner) {
        var document = new OutlineDocument();
        document.setWorkspaceId(workspace.getId());
        document.setConnectionId(owner.getId());
        document.setDocumentId(sourceId);
        document.setCollectionId("collection");
        document.setCollectionSlug("engineering");
        document.setSlug(sourceId);
        document.setTitle("Design");
        document.setBodyMarkdown("Synthetic test body. ".repeat(5_000));
        return documents.saveAndFlush(document);
    }

    @Test
    void shouldReplaceScalarBodyReadsWithOneBodyFreeMetadataQuery() {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < 25; index++)
            ids.add(document("doc-" + index, connection).getId());
        var scalar = reads.measure(() -> ids.stream()
                .map(id -> documents
                        .findByWorkspaceIdAndIdForProjection(workspace.getId(), id)
                        .orElseThrow())
                .toList());
        var batch = reads.measure(() -> documents.findAccessByWorkspaceIdAndIdIn(workspace.getId(), ids));

        assertThat(batch.value())
                .extracting(OutlineDocumentRepository.DocumentAccessRow::getId)
                .containsExactlyInAnyOrderElementsOf(
                        scalar.value().stream().map(OutlineDocument::getId).toList());
        assertThat(batch.value())
                .allSatisfy(row -> assertThat(row.getBodyAvailable()).isTrue());
        assertThat(scalar.cost().statements()).isEqualTo(25);
        assertThat(scalar.cost().entities()).isEqualTo(25);
        assertThat(batch.cost()).isEqualTo(new SqlReadMeasurement.Cost(1, 0));
        assertThat(batch.sql()).singleElement().satisfies(sql -> {
            String select = sql.substring(0, sql.indexOf(" from "));
            assertThat(select).contains("body_markdown is not null");
            assertThat(StringUtils.countOccurrencesOf(select, "body_markdown")).isEqualTo(1);
        });
    }

    @Test
    void shouldPreserveDeletionBodyAvailabilityReferencesAndWorkspaceScope() {
        var live = document("live", connection);
        var deleted = document("deleted", connection);
        deleted.setDeletedAt(Instant.now());
        documents.saveAndFlush(deleted);
        var evicted = document("evicted", connection);
        evicted.setBodyMarkdown(null);
        documents.saveAndFlush(evicted);
        var ids = List.of(live.getId(), deleted.getId(), evicted.getId());

        var byId = reads.measure(() -> documents.findAccessByWorkspaceIdAndIdIn(workspace.getId(), ids));
        var byReference = reads.measure(() -> documents.findAccessByWorkspaceIdAndReferenceIn(
                workspace.getId(), List.of("live", "deleted", "evicted")));

        assertThat(byId.value()).hasSize(3).allSatisfy(row -> {
            assertThat(row.getDeleted()).isEqualTo(row.getId().equals(deleted.getId()));
            assertThat(row.getBodyAvailable()).isEqualTo(!row.getId().equals(evicted.getId()));
        });
        assertThat(byReference.value())
                .extracting(OutlineDocumentRepository.DocumentAccessRow::getId)
                .containsExactlyInAnyOrderElementsOf(ids);
        assertThat(byReference.cost()).isEqualTo(new SqlReadMeasurement.Cost(1, 0));
        var foreign = workspaces.save(WorkspaceTestFixtures.activeWorkspace("outline-alt-" + UUID.randomUUID()));
        assertThat(documents.findAccessByWorkspaceIdAndIdIn(foreign.getId(), ids))
                .isEmpty();
        assertThat(documents.findAccessByWorkspaceIdAndReferenceIn(foreign.getId(), List.of("live")))
                .isEmpty();
    }

    @Test
    void shouldApplyNativeCreatorEditorAndCollaboratorSuppressionAtTheDocumentConnectionOrigin() {
        String subject = "subject-" + UUID.randomUUID();
        var creator = document("creator", connection);
        creator.setCreatedBySubject(subject);
        documents.saveAndFlush(creator);
        var editor = document("editor", connection);
        editor.setUpdatedBySubject(subject);
        documents.saveAndFlush(editor);
        var collaborator = document("collaborator", connection);
        collaborator.setCollaboratorSubjects(List.of(subject));
        documents.saveAndFlush(collaborator);
        var otherOrigin = document("other-origin", connect("https://outline-other.example.com"));
        otherOrigin.setCreatedBySubject(subject);
        documents.saveAndFlush(otherOrigin);
        var ids = List.of(creator.getId(), editor.getId(), collaborator.getId(), otherOrigin.getId());
        Long provider = jdbc.queryForObject("""
                INSERT INTO identity_provider(type,server_url,created_at)
                VALUES ('OUTLINE','https://outline-access.example.com',CURRENT_TIMESTAMP)
                ON CONFLICT(type,server_url) DO UPDATE SET server_url=EXCLUDED.server_url RETURNING id
                """, Long.class);
        var suppression = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person_suppression(id,provider_id,subject,team_key) VALUES (?,?,?,'')",
                suppression,
                provider,
                subject);

        assertThat(documents.findAccessByWorkspaceIdAndIdIn(workspace.getId(), ids))
                .extracting(OutlineDocumentRepository.DocumentAccessRow::getId)
                .containsExactly(otherOrigin.getId());
        assertThat(documents.findAccessByWorkspaceIdAndReferenceIn(
                        workspace.getId(), List.of("creator", "editor", "collaborator", "other-origin")))
                .extracting(OutlineDocumentRepository.DocumentAccessRow::getId)
                .containsExactly(otherOrigin.getId());
        jdbc.update("DELETE FROM person_suppression WHERE id=?", suppression);
        assertThat(documents.findAccessByWorkspaceIdAndIdIn(workspace.getId(), ids))
                .hasSize(4);
    }
}
