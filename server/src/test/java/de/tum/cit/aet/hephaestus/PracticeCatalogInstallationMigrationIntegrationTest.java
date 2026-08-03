package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("integration")
class PracticeCatalogInstallationMigrationIntegrationTest {

    private static final String MARKER_CHANGELOG = "1785274902740_changelog.xml";
    private static final String BEFORE_CATALOG_TAG = "before-instance-curated-catalog";
    private static final String MASTER = "db/master.xml";

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_practice_catalog_installation_migration")
        .withUsername("test")
        .withPassword("test");

    @Test
    void shouldMigrateCatalogStateAndWorkspaceRevisionPointers() throws Exception {
        updateUntilBefore(MARKER_CHANGELOG);
        seedExistingWorkspaces();
        updateOnly(MARKER_CHANGELOG);
        execute("DELETE FROM practice_catalog_installation WHERE workspace_id = 136104");
        execute(
            """
            UPDATE practice_catalog_installation
            SET installed_at = (
                SELECT dateexecuted + INTERVAL '1 second'
                FROM databasechangelog
                WHERE id = '1785274902740-4' AND author = 'hephaestus'
            )
            WHERE workspace_id = 136103
            """
        );

        try (Liquibase liquibase = liquibase()) {
            liquibase.tag(BEFORE_CATALOG_TAG);
        }
        updateThrough("1785743133884-4");
        execute(
            "UPDATE practice_revision SET detection_fingerprint = repeat('a', 64) " +
                "WHERE practice_id = 136301 AND revision_number = " +
                "(SELECT max(revision_number) FROM practice_revision WHERE practice_id = 136301)",
            "UPDATE practice SET source_curated_slug = 'second-practice', " +
                "source_curated_fingerprint = repeat('c', 64) WHERE id = 136302"
        );
        try (Liquibase liquibase = liquibase()) {
            liquibase.update(contexts());
        }

        assertMarkerRepair();
        assertHistoricalFingerprintsVersioned();
        assertEmptyCatalogBootstrap();
        assertWorkspaceRevisionBackfill();
        assertEvidenceRevisionBackfill();
        assertPointerOwnership();
        assertControlledProvenanceUpdate();
        assertAggregateDeletion();
        assertProjectionInvariant();
        assertRevisionImmutability();
        appendValidCurrentRevision();
        assertRollbackAndReapply();
    }

    private static void assertHistoricalFingerprintsVersioned() throws SQLException {
        assertThat(
            scalar(
                "SELECT detection_fingerprint FROM practice_revision " +
                    "WHERE practice_id = 136301 ORDER BY revision_number DESC LIMIT 1"
            )
        ).isEqualTo("v1:" + "a".repeat(64));
        assertThat(scalar("SELECT source_curated_fingerprint FROM practice WHERE id = 136302")).isEqualTo(
            "v1:" + "c".repeat(64)
        );
    }

    private static void assertMarkerRepair() throws SQLException {
        assertThat(markedWorkspaceSlugs()).containsExactly("area-only", "practice-only");
    }

    private static void assertEmptyCatalogBootstrap() throws SQLException {
        assertThat(scalar("SELECT count(*)::text FROM curated_practice_override")).isEqualTo("0");
        assertThat(scalar("SELECT count(*)::text FROM curated_area_override")).isEqualTo("0");
        assertThat(
            scalar("SELECT count(*)::text FROM practice_catalog_installation WHERE provenance_linked_at IS NOT NULL")
        ).isEqualTo("1");
        assertThat(
            scalar("SELECT count(*)::text FROM practice_catalog_installation WHERE provenance_linked_at IS NULL")
        ).isEqualTo("1");
        assertThatThrownBy(() ->
            execute(
                """
                INSERT INTO curated_practice_override (slug, created_at, updated_at, version)
                VALUES ('says-nothing', now(), now(), 0)
                """
            )
        ).isInstanceOf(SQLException.class);
    }

    private static void assertWorkspaceRevisionBackfill() throws SQLException {
        assertThat(
            scalar(
                """
                SELECT count(*)::text
                FROM practice practice
                JOIN practice_revision revision ON revision.id = practice.current_revision_id
                LEFT JOIN practice_area area ON area.id = practice.practice_area_id
                WHERE practice.id IN (136301, 136302)
                  AND revision.practice_id = practice.id
                  AND revision.slug = practice.slug
                  AND revision.name = practice.name
                  AND revision.applies_to = practice.applies_to
                  AND revision.trigger_events = practice.trigger_events
                  AND revision.criteria = practice.criteria
                  AND revision.precompute_script IS NOT DISTINCT FROM practice.precompute_script
                  AND revision.why_it_matters IS NOT DISTINCT FROM practice.why_it_matters
                  AND revision.what_good_looks_like IS NOT DISTINCT FROM practice.what_good_looks_like
                  AND revision.area_slug IS NOT DISTINCT FROM area.slug
                """
            )
        ).isEqualTo("2");
        assertThat(
            scalar(
                """
                SELECT revision.revision_number::text
                FROM practice practice
                JOIN practice_revision revision ON revision.id = practice.current_revision_id
                WHERE practice.id = 136301
                """
            )
        ).isEqualTo("3");
        assertThat(
            scalar(
                """
                SELECT count(*)::text
                FROM practice_revision
                WHERE practice_id = 136301
                  AND revision_number = 1
                  AND criteria = 'legacy criteria'
                  AND slug IS NULL
                  AND name IS NULL
                """
            )
        ).isEqualTo("1");
    }

    private static void assertPointerOwnership() {
        assertThatThrownBy(() ->
            execute(
                """
                UPDATE practice
                SET current_revision_id = (
                    SELECT current_revision_id FROM practice WHERE id = 136302
                )
                WHERE id = 136301
                """
            )
        ).isInstanceOf(SQLException.class);
    }

    private static void assertAggregateDeletion() throws SQLException {
        execute("DELETE FROM practice WHERE id = 136302");
        assertThat(scalar("SELECT count(*)::text FROM practice WHERE id = 136302")).isEqualTo("0");
        assertThat(scalar("SELECT count(*)::text FROM practice_revision WHERE practice_id = 136302")).isEqualTo("0");
    }

    private static void assertControlledProvenanceUpdate() throws SQLException {
        execute(
            """
            UPDATE practice_revision
            SET detection_fingerprint = ('v2:' || repeat('b', 64))
            WHERE id = (SELECT current_revision_id FROM practice WHERE id = 136302)
            """
        );
        assertThat(
            scalar(
                """
                SELECT count(*)::text
                FROM practice_revision
                WHERE id = (SELECT current_revision_id FROM practice WHERE id = 136302)
                  AND detection_fingerprint = ('v2:' || repeat('b', 64))
                """
            )
        ).isEqualTo("1");
        assertThatThrownBy(() ->
            execute(
                """
                UPDATE practice_revision
                SET detection_fingerprint = ('v2:' || repeat('d', 64))
                WHERE id = (SELECT current_revision_id FROM practice WHERE id = 136302)
                """
            )
        ).isInstanceOf(SQLException.class);

        execute(
            """
            UPDATE practice
            SET source_curated_slug = 'second-practice', source_curated_fingerprint = ('v2:' || repeat('b', 64))
            WHERE id = 136302
            """
        );
        assertThatThrownBy(() ->
            execute("UPDATE practice SET source_curated_fingerprint = NULL WHERE id = 136302")
        ).isInstanceOf(SQLException.class);
    }

    private static void assertProjectionInvariant() {
        assertThatThrownBy(() -> execute("UPDATE practice SET name = 'Diverged' WHERE id = 136301"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice current revision does not match its current projection");
        assertThatThrownBy(() -> execute("UPDATE practice SET current_revision_id = NULL WHERE id = 136301"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice current revision does not match its current projection");
        assertThatThrownBy(() ->
            execute(
                "UPDATE practice SET evidence_declaration = " +
                    "jsonb_set(evidence_declaration, '{profile}', '\"issue-review\"') WHERE id = 136301"
            )
        )
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice current revision does not match its current projection");
        assertThatThrownBy(() ->
            execute(
                """
                INSERT INTO practice (
                    workspace_id, slug, name, applies_to, display_order, trigger_events,
                    criteria, evidence_declaration, is_active, created_at
                ) VALUES (
                    136103, 'missing-current-revision', 'Missing revision', 'PULL_REQUEST', 3,
                    '[]'::jsonb, 'criteria', '{}'::jsonb, true, now()
                )
                """
            )
        )
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice current revision does not match its current projection");
    }

    private static void assertRevisionImmutability() throws SQLException {
        assertThatThrownBy(() ->
            execute("UPDATE practice_revision SET criteria = 'mutated' WHERE practice_id = 136301")
        )
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice revisions are immutable");

        assertThatThrownBy(() ->
            execute(
                "UPDATE practice_revision SET evidence_declaration = '{}'::jsonb " +
                    "WHERE id = (SELECT current_revision_id FROM practice WHERE id = 136301)"
            )
        )
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice revisions are immutable");

        execute(
            """
            UPDATE practice_revision
            SET detection_fingerprint = ('v2:' || repeat('a', 64))
            WHERE practice_id = 136301 AND slug IS NOT NULL AND detection_fingerprint IS NULL
            """
        );
        assertThat(
            scalar(
                "SELECT count(*)::text FROM practice_revision WHERE practice_id = 136301" +
                    " AND detection_fingerprint = ('v2:' || repeat('a', 64))"
            )
        ).isEqualTo("1");

        assertThatThrownBy(() ->
            execute(
                "UPDATE practice_revision SET detection_fingerprint = ('v2:' || repeat('b', 64))" +
                    " WHERE practice_id = 136301 AND slug IS NOT NULL"
            )
        )
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("practice revisions are immutable");
    }

    private static void appendValidCurrentRevision() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("UPDATE practice SET name = 'Updated practice' WHERE id = 136301");
            statement.execute(
                """
                INSERT INTO practice_revision (
                    practice_id, revision_number, criteria, created_at,
                    slug, name, applies_to, trigger_events, precompute_script,
                    why_it_matters, what_good_looks_like,
                    area_slug, area_name, area_description, area_icon, area_color,
                    detection_fingerprint, evidence_declaration
                )
                SELECT practice.id,
                       (SELECT max(revision_number) + 1 FROM practice_revision WHERE practice_id = practice.id),
                       practice.criteria,
                       now(),
                       practice.slug,
                       practice.name,
                       practice.applies_to,
                       practice.trigger_events,
                       practice.precompute_script,
                       practice.why_it_matters,
                       practice.what_good_looks_like,
                       area.slug,
                       area.name,
                       area.description,
                       area.icon,
                       area.color,
                       ('v2:' || repeat('a', 64)),
                       practice.evidence_declaration
                FROM practice
                LEFT JOIN practice_area area ON area.id = practice.practice_area_id
                WHERE practice.id = 136301
                """
            );
            statement.execute(
                """
                UPDATE practice
                SET current_revision_id = (
                    SELECT id
                    FROM practice_revision
                    WHERE practice_id = 136301
                    ORDER BY revision_number DESC
                    LIMIT 1
                )
                WHERE id = 136301
                """
            );
            connection.commit();
        }
        assertThat(scalar("SELECT name FROM practice WHERE id = 136301")).isEqualTo("Updated practice");
        assertThat(
            scalar(
                """
                SELECT revision_number::text
                FROM practice_revision
                WHERE id = (SELECT current_revision_id FROM practice WHERE id = 136301)
                """
            )
        ).isEqualTo("4");
    }

    private static void assertRollbackAndReapply() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            liquibase.rollback(BEFORE_CATALOG_TAG, contexts(), new LabelExpression());
        }

        assertThat(scalar("SELECT to_regclass('curated_practice_override')::text")).isNull();
        assertThat(
            scalar(
                """
                SELECT count(*)::text
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'practice'
                  AND column_name = 'current_revision_id'
                """
            )
        ).isEqualTo("0");
        assertThat(scalar("SELECT count(*)::text FROM practice_revision WHERE practice_id = 136301")).isEqualTo("1");
        assertThat(scalar("SELECT count(*)::text FROM practice_revision WHERE criteria = 'legacy criteria'")).isEqualTo(
            "1"
        );

        try (Liquibase liquibase = liquibase()) {
            liquibase.update(contexts());
        }

        assertMarkerRepair();
        assertEmptyCatalogBootstrap();
        assertThat(
            scalar(
                """
                SELECT revision.revision_number::text
                FROM practice practice
                JOIN practice_revision revision ON revision.id = practice.current_revision_id
                WHERE practice.id = 136301
                """
            )
        ).isEqualTo("3");
    }

    private static void assertEvidenceRevisionBackfill() throws SQLException {
        assertThat(
            scalar(
                """
                SELECT count(*)::text
                FROM practice practice
                JOIN practice_revision revision ON revision.id = practice.current_revision_id
                WHERE practice.id IN (136301, 136302)
                  AND revision.evidence_declaration = practice.evidence_declaration
                  AND revision.evidence_declaration IS NOT NULL
                """
            )
        ).isEqualTo("2");
        assertThat(
            scalar(
                """
                SELECT count(*)::text
                FROM practice_revision
                WHERE practice_id IN (136301, 136302)
                  AND evidence_declaration IS NULL
                """
            )
        ).isEqualTo("3");
    }

    private static void seedExistingWorkspaces() throws SQLException {
        execute(
            """
            INSERT INTO workspace (id, account_login, account_type, display_name, slug, status, is_publicly_viewable)
            VALUES (136101, 'migration-empty-marked',   'ORG', 'Empty marked',   'empty-marked',   'ACTIVE', false),
                   (136102, 'migration-area-only',      'ORG', 'Area only',      'area-only',      'ACTIVE', false),
                   (136103, 'migration-practice-only',  'ORG', 'Practice only',  'practice-only',  'ACTIVE', false),
                   (136104, 'migration-empty-unmarked', 'ORG', 'Empty unmarked', 'empty-unmarked', 'ACTIVE', false)
            """,
            """
            INSERT INTO practice_area (
                id, workspace_id, slug, name, is_active, display_order, icon, color, created_at
            ) VALUES
                (136201, 136102, 'existing-area', 'Existing area', true, 0, 'Package', 'sky', now()),
                (136202, 136103, 'practice-area', 'Practice area', true, 0, 'Package', 'sky', now())
            """,
            """
            INSERT INTO practice (
                id, workspace_id, practice_area_id, slug, name, applies_to, display_order,
                trigger_events, criteria, precompute_script, why_it_matters, what_good_looks_like,
                is_active, created_at
            ) VALUES
                (136301, 136103, 136202, 'existing-practice', 'Existing practice', 'PULL_REQUEST', 0,
                 '["PullRequestCreated"]'::jsonb, 'Current criteria', NULL, 'Why', 'Good', true, now()),
                (136302, 136103, NULL, 'second-practice', 'Second practice', 'ISSUE', 1,
                 '["IssuesEvent"]'::jsonb, 'Second criteria', NULL, NULL, NULL, true, now())
            """,
            """
            INSERT INTO practice_revision (practice_id, revision_number, criteria, created_at)
            VALUES (136301, 1, 'legacy criteria', now())
            """
        );
    }

    private static void updateUntilBefore(String changelogName) throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(contexts(), new LabelExpression());
            List<Integer> indexes = indexesOf(pending, changelogName);
            assertThat(indexes).as("%s must be present in the pending changelog", changelogName).isNotEmpty();
            liquibase.update(indexes.getFirst(), contexts(), new LabelExpression());
        }
    }

    private static void updateOnly(String changelogName) throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(contexts(), new LabelExpression());
            List<Integer> indexes = indexesOf(pending, changelogName);
            assertThat(indexes).as("%s must be the next pending changelog", changelogName).isNotEmpty().startsWith(0);
            liquibase.update(indexes.size(), contexts(), new LabelExpression());
        }
    }

    private static void updateThrough(String changeSetId) throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(contexts(), new LabelExpression());
            int index = java.util.stream.IntStream.range(0, pending.size())
                .filter(candidate -> pending.get(candidate).getId().equals(changeSetId))
                .findFirst()
                .orElseThrow();
            liquibase.update(index + 1, contexts(), new LabelExpression());
        }
    }

    private static List<Integer> indexesOf(List<ChangeSet> changesets, String changelogName) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < changesets.size(); index++) {
            if (changesets.get(index).getFilePath().endsWith(changelogName)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static List<String> markedWorkspaceSlugs() throws SQLException {
        List<String> slugs = new ArrayList<>();
        try (
            Connection connection = connect();
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(
                """
                SELECT workspace.slug
                FROM practice_catalog_installation installation
                JOIN workspace workspace ON workspace.id = installation.workspace_id
                WHERE workspace.id BETWEEN 136101 AND 136104
                ORDER BY workspace.slug
                """
            )
        ) {
            while (rows.next()) {
                slugs.add(rows.getString(1));
            }
        }
        return slugs;
    }

    private static Liquibase liquibase() throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
            new JdbcConnection(connect())
        );
        return new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
    }

    private static Contexts contexts() {
        return new Contexts("prod");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static String scalar(String sql) throws SQLException {
        try (
            Connection connection = connect();
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(sql)
        ) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
