package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Definition and catalog proof uses released SQL rows, independent of the current Java model. */
@Tag("database")
class PracticeV1DefinitionMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldTransformEverySupportedProgramWithoutChangingItsPinnedSource(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/released-programs.json"))) {
            var programs = Objects.requireNonNull(JsonMapper.builder().build().readTree(input));
            assertThat(programs.size()).isEqualTo(34);
            try (var connection =
                    DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
                connection.setAutoCommit(false);
                int index = 0;
                for (var program : programs) {
                    long practiceId = 1000000L + index;
                    long versionId = 1100000L + index;
                    try (var statement = connection.prepareStatement("""
                        INSERT INTO practice(id,workspace_id,slug,name,criteria,applies_to,subject,signals,
                            evidence_requirements,review_when,automated_review_policy,delivery_behavior,precompute_script,created_at)
                        SELECT ?,workspace_id,?,name,criteria,applies_to,subject,signals,evidence_requirements,
                            review_when,automated_review_policy,delivery_behavior,?,created_at FROM practice WHERE id=992121
                        """)) {
                        statement.setLong(1, practiceId);
                        statement.setString(2, "program-" + index);
                        statement.setString(3, program.path("source").asString());
                        statement.executeUpdate();
                    }
                    try (var statement = connection.prepareStatement("""
                        INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,
                            signals,evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,
                            precompute_script,created_at)
                        SELECT ?,id,1,slug,name,applies_to,subject,signals,evidence_requirements,review_when,
                            criteria,automated_review_policy,delivery_behavior,precompute_script,'2026-01-01'
                        FROM practice WHERE id=?
                        """)) {
                        statement.setLong(1, versionId);
                        statement.setLong(2, practiceId);
                        statement.executeUpdate();
                    }
                    try (var statement =
                            connection.prepareStatement("UPDATE practice SET current_revision_id=? WHERE id=?")) {
                        statement.setLong(1, versionId);
                        statement.setLong(2, practiceId);
                        statement.executeUpdate();
                    }
                    index++;
                }
                connection.commit();
            }
            PracticeV1CutoverMigrationTest.upgrade(database, context);
            int index = 0;
            for (var program : programs) {
                assertThat(PracticeV1CutoverMigrationTest.column(
                                database,
                                "SELECT encode(sha256(convert_to(definition->>'script','UTF8')),'hex') FROM"
                                        + " practice_version WHERE id="
                                        + (1100000L + index)))
                        .containsExactly(program.path("sourceSha256").asString());
                assertThat(PracticeV1CutoverMigrationTest.column(
                                database,
                                "SELECT encode(sha256(convert_to(v.definition->>'script','UTF8')),'hex') || '|' ||"
                                        + " v.number FROM practice p JOIN practice_version v ON v.id=p.current_version_id"
                                        + " WHERE p.id="
                                        + (1000000L + index)))
                        .containsExactly(program.path("targetSha256").asString() + "|2");
                index++;
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldAppendAnExactBundledProgramTransformationAndKeepPinnedHistory(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/released-programs.json"))) {
            var program = Objects.requireNonNull(
                    JsonMapper.builder().build().readTree(input).get(0));
            installProgram(database, program.path("source").asString());
            PracticeV1CutoverMigrationTest.upgrade(database, context);
            assertThat(PracticeV1CutoverMigrationTest.column(
                            database,
                            "SELECT encode(sha256(convert_to(definition->>'script','UTF8')),'hex') FROM"
                                    + " practice_version WHERE id=992132"))
                    .containsExactly(program.path("sourceSha256").asString());
            assertThat(PracticeV1CutoverMigrationTest.column(
                            database,
                            "SELECT encode(sha256(convert_to(v.definition->>'script','UTF8')),'hex') || '|' ||"
                                    + " v.number || '|' || (v.definition->'reads'->>'contract') FROM practice p"
                                    + " JOIN practice_version v ON v.id=p.current_version_id WHERE p.id=992121"))
                    .containsExactly(program.path("targetSha256").asString() + "|3|2.0.0");
            assertThat(PracticeV1CutoverMigrationTest.column(
                            database,
                            "SELECT practice_version_id::text FROM result WHERE"
                                    + " id='00000000-0000-0000-0000-000000992141'"))
                    .containsExactly("992131");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldIdentifyUnsupportedCustomProgramsAndRollBackTheCutover(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        installProgram(database, "export default async function custom() { return {outcome: 'MET'}; }");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.upgrade(database, context))
                .hasMessageContaining("practice 992121 has an unsupported custom program");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database, "SELECT current_revision_id::text FROM practice WHERE id=992121"))
                .containsExactly("992132");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND"
                                + " table_name='result'"))
                .containsExactly("0");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database, "SELECT locked::text FROM databasechangeloglock WHERE id=1"))
                .containsExactly("false");
    }

    private static void installProgram(TestDatabase database, String source) throws SQLException {
        try (var connection =
                DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("""
                INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,
                    signals,evidence_requirements,review_when,criteria,automated_review_policy,
                    delivery_behavior,precompute_script,created_at)
                SELECT 992132,id,2,slug,name,applies_to,subject,signals,evidence_requirements,review_when,
                    criteria,automated_review_policy,delivery_behavior,?,'2026-01-02T00:00:00Z'
                FROM practice WHERE id=992121
                """)) {
                statement.setString(1, source);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "UPDATE practice SET current_revision_id=992132,precompute_script=? WHERE id=992121")) {
                statement.setString(1, source);
                statement.executeUpdate();
            }
            connection.commit();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepPinnedHistoryAndCustomizedCatalogBases(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO practice_group(id,workspace_id,slug,name,description,autonomy,created_at,display_order,visible_in_practice_dashboards)
            VALUES(993001,992101,'review-style','Review style','A workspace group.','HUMAN_APPROVAL','2026-01-01T00:00:00Z',0,true);
            INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,
                signals,evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,created_at)
            SELECT 992132,id,2,slug,name,applies_to,subject,signals,evidence_requirements,review_when,
                'Changed standard.',automated_review_policy,delivery_behavior,'2026-01-02T00:00:00Z'
            FROM practice WHERE id=992121;
            UPDATE practice SET criteria='Changed standard.',current_revision_id=992132,
                source_curated_slug='local-offer',source_curated_fingerprint='v5:'||repeat('a',64),
                adopted_base_source='EXACT_ADOPTION',
                adopted_base=jsonb_build_object('name',name,'signals',signals,'reviewWhen',review_when,
                    'subject',subject,'precondition',precondition,'criteria','Original standard.',
                    'evidenceRequirements',evidence_requirements,'automatedReviewPolicy',automated_review_policy,
                    'precomputeScript',precompute_script,'deliveryBehavior',delivery_behavior,
                    'whyItMatters',why_it_matters,'whatGoodLooksLike',what_good_looks_like,
                    'groupSlug',NULL,'visual',visual,'guide',guide)
                WHERE id=992121;
            INSERT INTO curated_practice_override(slug,name,applies_to,criteria,subject,signals,
                evidence_requirements,review_when,automated_review_policy,delivery_behavior,position,version,created_at,updated_at)
            SELECT 'local-offer','Local offer',applies_to,'Local offer standard.',subject,signals,
                evidence_requirements,review_when,automated_review_policy,delivery_behavior,2,7,'2026-01-01T00:00:00Z','2026-01-02T00:00:00Z'
                FROM practice WHERE id=992121;
            INSERT INTO curated_group_override(slug,name,description,position,version,created_at,updated_at)
            VALUES('local-group','Local group','Keep this group.',3,9,'2026-01-01T00:00:00Z','2026-01-02T00:00:00Z');
            INSERT INTO curated_practice_override(slug,position,retired_at,version,created_at,updated_at)
            VALUES('excluded-following-entry',4,'2026-01-02T00:00:00Z',11,'2026-01-01T00:00:00Z','2026-01-02T00:00:00Z');
            INSERT INTO practice_catalog_installation(workspace_id,installed_at,provenance_linked_at)
            VALUES(992101,'2026-01-01T00:00:00Z','2026-01-02T00:00:00Z');
            """);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT number || '|' || (definition->>'criteria') FROM practice_version WHERE id"
                                + " IN(992131,992132) ORDER BY number"))
                .containsExactly("1|Explain the purpose of the change.", "2|Changed standard.");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT p.catalog_slug || '|' || (p.catalog_base->'definition'->>'criteria') || '|' ||"
                                + " (v.definition->>'criteria') || '|' || v.number FROM practice p JOIN"
                                + " practice_version v ON v.id=p.current_version_id WHERE p.id=992121"))
                .containsExactly("local-offer|Original standard.|Changed standard.|3");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database, "SELECT feedback_mode FROM practice_group WHERE id=993001"))
                .containsExactly("APPROVE_FIRST");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT kind || '|' || (definition->>'criteria') || '|' || version FROM catalog_entry"
                                + " WHERE entry_type='PRACTICE' AND slug='local-offer'"))
                .containsExactly("scm.pull_request|Local offer standard.|7");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (kind IS NULL AND definition IS NULL AND base IS NULL AND excluded_at IS NOT"
                                + " NULL)::text || '|' || version FROM catalog_entry WHERE entry_type='PRACTICE'"
                                + " AND slug='excluded-following-entry'"))
                .containsExactly("true|11");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (review_digest=practice_review_digest(definition))::text FROM practice_version"
                                + " WHERE practice_id=992121 ORDER BY number"))
                .containsExactly("true", "true", "true");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (practice_catalog_installed_at='2026-01-01T00:00:00Z'::timestamptz)::text FROM"
                                + " workspace WHERE id=992101"))
                .containsExactly("true");
        assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                        database,
                        "UPDATE practice_version SET definition=jsonb_set(definition,'{criteria}','\"Rewrite"
                                + " history\"'::jsonb) WHERE id=992131"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("immutable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldAppendFinalBundledDefinitionsWithoutChangingPinnedOrCustomizedStandards(String context)
            throws Exception {
        var mapper = JsonMapper.builder().build();
        final JsonNode released;
        final JsonNode targets;
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/released-catalog.json"))) {
            released = Objects.requireNonNull(mapper.readTree(input));
        }
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/catalog-targets.json"))) {
            targets = Objects.requireNonNull(mapper.readTree(input));
        }
        assertThat(targets.size()).isEqualTo(52);
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        try (var connection =
                DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
            connection.setAutoCommit(false);
            int index = 0;
            for (var entry : released) {
                if (!entry.path("entryType").asString().equals("PRACTICE")) continue;
                seedBundledPractice(connection, 1300000L + index, 1400000L + index, entry, null);
                if (entry.path("slug").asString().equals("keeps-functions-small-and-single-purpose")) {
                    seedBundledPractice(connection, 1300100L, 1400100L, entry, "The workspace's customized standard.");
                }
                index++;
            }
            assertThat(index).isEqualTo(52);
            connection.commit();
        }
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        for (var target : targets) {
            if (!(target.path("definition").deepCopy() instanceof ObjectNode expected)) {
                throw new IllegalStateException("The catalog target needs a complete definition object.");
            }
            expected.remove("script");
            var actual = PracticeV1CutoverMigrationTest.column(
                    database,
                    "SELECT (v.definition - 'script')::text FROM practice p JOIN practice_version v"
                            + " ON v.id=p.current_version_id WHERE p.slug='bundle-"
                            + target.path("slug").asString() + "'");
            assertThat(actual).hasSize(1);
            assertThat(mapper.readTree(Objects.requireNonNull(actual.getFirst())))
                    .isEqualTo(expected);
        }
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT count(*)::text FROM practice_version WHERE id BETWEEN 1400000 AND 1400051
                AND number=1 AND definition->'scan'='null'::jsonb AND definition->'reads'->>'contract'='1.3.0'
            """)).containsExactly("52");
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT count(*)::text FROM practice p JOIN practice_version v ON v.id=p.current_version_id
            WHERE p.id BETWEEN 1300000 AND 1300051 AND v.number=2 AND v.definition->'scan'<>'null'::jsonb
            """)).containsExactly("13");
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT v.definition->>'criteria' || '|' || (v.definition->'scan'='null'::jsonb)::text
                || '|' || v.number FROM practice p JOIN practice_version v ON v.id=p.current_version_id WHERE p.id=1300100
            """))
                .containsExactly("The workspace's customized standard.|true|2");
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT definition->>'criteria' FROM practice_version WHERE id=1400100
            """))
                .containsExactly("The workspace's customized standard.");
    }

    private static void seedBundledPractice(
            Connection connection, long practiceId, long versionId, JsonNode entry, @Nullable String customizedCriteria)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO practice(id,workspace_id,slug,name,criteria,applies_to,subject,signals,
                evidence_requirements,review_when,automated_review_policy,delivery_behavior,precondition,
                precompute_script,why_it_matters,what_good_looks_like,visual,guide,
                source_curated_slug,source_curated_fingerprint,created_at,updated_at)
            SELECT ?,992101,?,d->>'name',coalesce(?,d->>'criteria'),?,d->>'subject',d->'signals',
                d->'evidenceRequirements',d->'reviewWhen',d->'automatedReviewPolicy',d->'deliveryBehavior',nullif(d->'precondition','null'::jsonb),
                d->>'precomputeScript',d->>'whyItMatters',d->>'whatGoodLooksLike',nullif(d->'visual','null'::jsonb),nullif(d->'guide','null'::jsonb),?,?,
                '2026-01-01T00:00:00Z','2026-01-01T00:00:00Z' FROM (SELECT ?::jsonb AS d) authored
            """)) {
            statement.setLong(1, practiceId);
            statement.setString(
                    2,
                    (customizedCriteria == null ? "bundle-" : "custom-")
                            + entry.path("slug").asString());
            statement.setString(3, customizedCriteria);
            statement.setString(4, entry.path("kind").asString());
            statement.setString(5, entry.path("slug").asString());
            statement.setString(6, entry.path("oldReviewFingerprint").asString());
            statement.setString(7, entry.path("oldDefinition").toString());
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
            INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,
                signals,evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,
                precondition,precompute_script,why_it_matters,what_good_looks_like,visual,guide,created_at)
            SELECT ?,id,1,slug,name,applies_to,subject,signals,evidence_requirements,review_when,criteria,
                automated_review_policy,delivery_behavior,precondition,precompute_script,why_it_matters,
                what_good_looks_like,visual,guide,'2026-01-01T00:00:00Z' FROM practice WHERE id=?
            """)) {
            statement.setLong(1, versionId);
            statement.setLong(2, practiceId);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("UPDATE practice SET current_revision_id=? WHERE id=?")) {
            statement.setLong(1, versionId);
            statement.setLong(2, practiceId);
            statement.executeUpdate();
        }
    }
}
