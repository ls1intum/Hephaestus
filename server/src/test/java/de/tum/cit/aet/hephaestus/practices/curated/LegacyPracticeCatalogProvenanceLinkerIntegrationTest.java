package de.tum.cit.aet.hephaestus.practices.curated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

class LegacyPracticeCatalogProvenanceLinkerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private BundledCuratedCatalogReconciler reconciler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionOperations transactionOperations;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
            """
            INSERT INTO curated_catalog_sync_state (
                source, catalog_revision, content_digest, synchronized_at,
                provenance_backfill_version, provenance_backfilled_at
            ) VALUES ('BUNDLED', 0, NULL, NULL, 0, NULL)
            """
        );
        User owner = persistUser("legacy-catalog-owner");
        workspace = createWorkspace("legacy-catalog", "Legacy catalog", "legacy-catalog", AccountType.ORG, owner);
    }

    @Test
    void shouldRollbackFailedBackfillAndAtomicallyLinkOnlySeedEquivalentPracticesOnRetry() {
        insertLegacyPractice("matching", "Matching", "Seed criteria", 0);
        insertLegacyPractice("edited", "Edited", "Edited criteria", 1);
        BundledPracticeCatalog catalog = catalog();
        installMarkerFailure();

        try {
            assertThatThrownBy(() -> reconciler.reconcile(catalog))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("forced provenance marker failure");
        } finally {
            removeMarkerFailure();
        }

        assertThat(state()).containsExactly(0L, 0L);
        assertThat(count("SELECT count(*) FROM curated_practice")).isZero();
        assertThat(count("SELECT count(*) FROM practice WHERE source_curated_practice_id IS NOT NULL")).isZero();

        reconciler.reconcile(catalog);

        assertThat(state()).containsExactly(1L, 1L);
        assertThat(
            count("SELECT count(*) FROM practice WHERE slug = 'matching' AND source_curated_practice_id IS NOT NULL")
        ).isOne();
        assertThat(
            count(
                """
                SELECT count(*)
                FROM practice local
                JOIN practice_revision revision ON revision.id = local.current_revision_id
                JOIN curated_practice source ON source.id = local.source_curated_practice_id
                WHERE local.slug = 'matching'
                  AND revision.equivalent_curated_revision_id = source.latest_bundled_revision_id
                  AND revision.detection_fingerprint IS NOT NULL
                """
            )
        ).isOne();
        assertThat(
            count("SELECT count(*) FROM practice WHERE slug = 'edited' AND source_curated_practice_id IS NOT NULL")
        ).isZero();
        assertThat(
            count(
                """
                SELECT count(*)
                FROM practice_revision revision
                JOIN practice local ON local.current_revision_id = revision.id
                WHERE local.slug = 'edited'
                  AND revision.equivalent_curated_revision_id IS NOT NULL
                """
            )
        ).isZero();
    }

    private void insertLegacyPractice(String slug, String name, String criteria, int displayOrder) {
        transactionOperations.executeWithoutResult(ignored -> {
            Long practiceId = jdbcTemplate.queryForObject(
                """
                INSERT INTO practice (
                    workspace_id, slug, name, applies_to, display_order, trigger_events,
                    criteria, why_it_matters, is_active, created_at
                ) VALUES (?, ?, ?, 'PULL_REQUEST', ?, '["PullRequestCreated"]'::jsonb, ?,
                    'Locally edited guidance', true, now())
                RETURNING id
                """,
                Long.class,
                workspace.getId(),
                slug,
                name,
                displayOrder,
                criteria
            );
            Long revisionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO practice_revision (
                    practice_id, revision_number, slug, name, applies_to, trigger_events,
                    criteria, why_it_matters, created_at
                ) VALUES (?, 1, ?, ?, 'PULL_REQUEST', '["PullRequestCreated"]'::jsonb, ?,
                    'Locally edited guidance', now())
                RETURNING id
                """,
                Long.class,
                practiceId,
                slug,
                name,
                criteria
            );
            jdbcTemplate.update("UPDATE practice SET current_revision_id = ? WHERE id = ?", revisionId, practiceId);
        });
    }

    private static BundledPracticeCatalog catalog() {
        List<BundledPracticeCatalog.BundledPractice> practices = List.of(
            bundled("matching", "Matching", "Seed criteria"),
            bundled("edited", "Edited", "Seed criteria")
        );
        return new BundledPracticeCatalog(1, "c".repeat(64), List.of(), practices);
    }

    private static BundledPracticeCatalog.BundledPractice bundled(String slug, String name, String criteria) {
        PracticeDefinition definition = new PracticeDefinition(
            name,
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            criteria,
            null,
            "Bundled guidance",
            null,
            null
        );
        return new BundledPracticeCatalog.BundledPractice(slug, definition, definition.digest(slug));
    }

    private List<Long> state() {
        return jdbcTemplate.queryForObject("""
        SELECT catalog_revision, provenance_backfill_version
        FROM curated_catalog_sync_state
        WHERE source = 'BUNDLED'
        """, (resultSet, rowNum) -> List.of(resultSet.getLong(1), resultSet.getLong(2)));
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private void installMarkerFailure() {
        jdbcTemplate.execute(
            """
            CREATE FUNCTION fail_provenance_marker_for_test()
            RETURNS trigger
            LANGUAGE plpgsql
            AS $failure$
            BEGIN
                RAISE EXCEPTION 'forced provenance marker failure' USING ERRCODE = '55000';
            END;
            $failure$
            """
        );
        jdbcTemplate.execute(
            """
            CREATE TRIGGER fail_provenance_marker_for_test
            BEFORE UPDATE OF provenance_backfill_version ON curated_catalog_sync_state
            FOR EACH ROW
            WHEN (NEW.provenance_backfill_version = 1)
            EXECUTE FUNCTION fail_provenance_marker_for_test()
            """
        );
    }

    private void removeMarkerFailure() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_provenance_marker_for_test ON curated_catalog_sync_state");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_provenance_marker_for_test()");
    }
}
