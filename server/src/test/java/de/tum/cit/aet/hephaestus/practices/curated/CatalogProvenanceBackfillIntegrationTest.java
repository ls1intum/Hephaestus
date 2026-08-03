package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
class CatalogProvenanceBackfillIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String SHIPPED_SLUG = "describe-what-and-why";

    @Autowired
    private CatalogProvenanceBackfill backfill;

    @Autowired
    private CuratedCatalogService catalogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionOperations transactionOperations;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace matching;
    private Workspace edited;

    @BeforeEach
    void setUp() {
        User owner = persistUser("legacy-catalog-owner");
        matching = createWorkspace("legacy-one", "Legacy one", "legacy-one", AccountType.ORG, owner);
        edited = createWorkspace("legacy-two", "Legacy two", "legacy-two", AccountType.ORG, owner);
    }

    @Test
    void stampsEachEligibleInstallationThatStillMatchesTheBundledCatalog() {
        seedLegacyWorkspace(matching, shipped().criteria(), true);
        seedLegacyWorkspace(edited, shipped().criteria(), true);

        CatalogProvenanceBackfill.Stamped stamped = backfill.run();

        assertThat(stamped.practices()).isEqualTo(2);
        assertThat(stampedPractices(matching)).isOne();
        assertThat(stampedPractices(edited)).isOne();
        assertThat(unfingerprintedRevisions()).isZero();
        assertThat(workspacesAwaiting()).isZero();
    }

    @Test
    void looksAtEachWorkspaceOnlyOnce() {
        seedLegacyWorkspace(matching, shipped().criteria(), true);
        backfill.run();

        assertThat(backfill.run().practices()).isZero();
    }

    @Test
    void leavesAnEditedLegacySeedUnlinked() {
        seedLegacyWorkspace(matching, "The workspace changed these criteria", true);
        seedLegacyWorkspace(edited, shipped().criteria(), false);

        CatalogProvenanceBackfill.Stamped stamped = backfill.run();

        assertThat(stamped.practices()).isZero();
        assertThat(stampedPractices(matching)).isZero();
        assertThat(unfingerprintedRevisions()).isZero();
        assertThat(workspacesAwaiting()).isZero();
    }

    private PracticeDefinition shipped() {
        return catalogService.catalog().practice(SHIPPED_SLUG).orElseThrow().effective();
    }

    private void seedLegacyWorkspace(Workspace workspace, String criteria, boolean provenancePending) {
        PracticeDefinition shipped = shipped();
        transactionOperations.executeWithoutResult(ignored -> {
            Long areaId = jdbcTemplate.queryForObject(
                """
                INSERT INTO practice_area (workspace_id, slug, name, is_active, display_order, created_at)
                VALUES (?, ?, 'Area', true, 0, now())
                RETURNING id
                """,
                Long.class,
                workspace.getId(),
                shipped.areaSlug()
            );
            Long practiceId = jdbcTemplate.queryForObject(
                """
                INSERT INTO practice (
                    workspace_id, practice_area_id, slug, name, applies_to, display_order, trigger_events,
                    criteria, evidence_declaration, why_it_matters, is_active, created_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?::jsonb, ?, ?::jsonb, 'Reviewers need context', true, now())
                RETURNING id
                """,
                Long.class,
                workspace.getId(),
                areaId,
                SHIPPED_SLUG,
                shipped.name(),
                shipped.artifactType().name(),
                triggerEventsJson(shipped),
                criteria,
                evidenceJson(shipped)
            );
            Long revisionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO practice_revision (
                    practice_id, revision_number, slug, name, applies_to, trigger_events, criteria,
                    evidence_declaration, why_it_matters, area_slug, created_at
                ) VALUES (?, 1, ?, ?, ?, ?::jsonb, ?, ?::jsonb, 'Reviewers need context', ?, now())
                RETURNING id
                """,
                Long.class,
                practiceId,
                SHIPPED_SLUG,
                shipped.name(),
                shipped.artifactType().name(),
                triggerEventsJson(shipped),
                criteria,
                evidenceJson(shipped),
                shipped.areaSlug()
            );
            jdbcTemplate.update("UPDATE practice SET current_revision_id = ? WHERE id = ?", revisionId, practiceId);
            jdbcTemplate.update(
                """
                INSERT INTO practice_catalog_installation (workspace_id, installed_at, provenance_linked_at)
                VALUES (?, now(), CASE WHEN ? THEN NULL ELSE now() END)
                """,
                workspace.getId(),
                provenancePending
            );
        });
    }

    private static String triggerEventsJson(PracticeDefinition definition) {
        return definition.triggerEventsJson().toString();
    }

    private String evidenceJson(PracticeDefinition definition) {
        return objectMapper.valueToTree(definition.evidence()).toString();
    }

    private long stampedPractices(Workspace workspace) {
        return count(
            "SELECT count(*) FROM practice WHERE workspace_id = ? AND source_curated_slug IS NOT NULL",
            workspace.getId()
        );
    }

    private long unfingerprintedRevisions() {
        return count("SELECT count(*) FROM practice_revision WHERE slug IS NOT NULL AND detection_fingerprint IS NULL");
    }

    private long workspacesAwaiting() {
        return count("SELECT count(*) FROM practice_catalog_installation WHERE provenance_linked_at IS NULL");
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
