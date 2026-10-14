package de.tum.cit.aet.hephaestus.core.database;

import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.column;
import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.execute;
import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.migratedDatabase;
import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.releasedDatabase;
import static de.tum.cit.aet.hephaestus.core.database.PracticeMigrationFixture.upgrade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1CutoverMigrationTest {
    private static final String RESULT = "00000000-0000-0000-0000-000000992141";
    private static final String FEEDBACK = "00000000-0000-0000-0000-000000992161";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveReleasedProofAndStopOldExecutionWithoutRepeatingDelivery(String context) throws Exception {
        var database = releasedDatabase(context);
        upgrade(database, context);
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/db/practice-cutover/assert-migrated.sql"))) {
            execute(database, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(column(
                        database,
                        "SELECT outcome || '|' || severity || '|' || evidence_version || '|' || (ended_at IS NULL)"
                                + " FROM result WHERE id='"
                                + RESULT + "'"))
                .containsExactly("NOT_MET|TRIVIAL|0|true");
        assertThat(column(
                        database,
                        "SELECT (p.id<>r.id)::text || '|' || jsonb_exists(p.citations->0,'sourcePath') || '|' ||"
                                + " (p.basis IS NULL) FROM problem p JOIN result r ON r.id=p.result_id WHERE r.id='"
                                + RESULT + "'"))
                .containsExactly("true|true|true");
        assertThat(column(database, """
            SELECT (citations->0->>'commit') || '|' || jsonb_exists(citations->0,'revision') || '|'
                || (citations->0->>'quote')
            FROM problem WHERE result_id='00000000-0000-0000-0000-000000992141'
            """)).containsExactly("a".repeat(40) + "|false|Add retry");
        assertThat(column(database, """
            SELECT jsonb_exists(evidence,'citations')::text FROM result
            WHERE id='00000000-0000-0000-0000-000000992141'
            """)).containsExactly("false");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM result r LEFT JOIN item i ON"
                                + " (i.id,i.workspace_id)=(r.item_id,r.workspace_id) WHERE r.id='"
                                + RESULT + "' AND i.id IS NULL"))
                .containsExactly("0");
        assertThat(column(database, "SELECT state || '|' || body FROM feedback WHERE id='" + FEEDBACK + "'"))
                .containsExactly("DELIVERED|Say why.");
        assertThat(column(database, "SELECT checks::text FROM feedback WHERE id='" + FEEDBACK + "'"))
                .containsExactly("{}");
        assertThat(column(
                        database,
                        "SELECT checks->'MIGRATION'->'feedback'->'" + FEEDBACK
                                + "'->'MIGRATION'->>'deliveryState' FROM review WHERE"
                                + " id='00000000-0000-0000-0000-000000992111'"))
                .containsExactly("DELIVERED");
        assertThat(column(
                        database,
                        "SELECT id::text || '|' || external_ref || '|' || state FROM feedback_copy WHERE feedback_id='"
                                + FEEDBACK + "'"))
                .containsExactly("00000000-0000-0000-0000-000000992171|provider:123|POSTED");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM feedback_send WHERE feedback_id='" + FEEDBACK
                                + "' AND state='PENDING'"))
                .containsExactly("0");
        assertThat(column(
                        database,
                        "SELECT f.state || '|' || (f.delivered_at IS NULL) || '|' || c.state || '|' || c.body"
                                + " FROM feedback f JOIN feedback_copy c ON c.feedback_id=f.id WHERE"
                                + " f.id='00000000-0000-0000-0000-000000992193'"))
                .containsExactly("UNCONFIRMED|true|WITHHELD|Words shown for this result.");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM feedback_send WHERE"
                                + " feedback_id='00000000-0000-0000-0000-000000992193'"))
                .containsExactly("0");
        assertThat(column(
                        database,
                        "SELECT workspace_id || '|' || (parts->0->>'text') || '|' || (parts->1->>'type') ||"
                                + " '|' || (parts->1->'data'->>'resultId') || '|' || (parts->1->'data' ?"
                                + " 'observationId') FROM chat_message WHERE"
                                + " id='00000000-0000-0000-0000-000000992192'"))
                .containsExactly(
                        "992101|Unrelated assistant text.|data-result|00000000-0000-0000-0000-000000992141|false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveUnclaimedAccountsAndTheirMemberships(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT a.status || '|' || count(m.workspace_id) FROM identity i JOIN account a ON"
                                + " a.id=i.account_id JOIN membership m ON m.account_id=a.id WHERE"
                                + " i.provider_id=992102 AND i.provider_user_key='104' GROUP BY a.status"))
                .containsExactly("UNCLAIMED|2");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM identity WHERE provider_id=992102 AND provider_user_key='105'"))
                .containsExactly("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldStopRuntimeJobsAndPreserveTheirReviewFacts(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT status || '|' || processing_status || '|' || cancellation_reason || '|' ||"
                                + " (worker_id IS NULL) FROM review_job WHERE"
                                + " id='00000000-0000-0000-0000-000000992112'"))
                .containsExactly("CANCELLED|DONE|CONTRACT_CHANGED|true");
        assertThat(column(
                        database,
                        "SELECT status || '|' || processing_status FROM review_job WHERE"
                                + " id='00000000-0000-0000-0000-000000992113'"))
                .containsExactly("COMPLETED|FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveItemAndEventIdentity(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT i.kind || '|' || i.source_id || '|' || (i.removed_at IS NOT NULL) || '|' ||"
                                + " p.kind || '|' || p.source_id || '|' || (i.workspace_id=p.workspace_id) FROM"
                                + " review r JOIN item i ON i.id=r.item_id JOIN item p ON p.id=i.parent_id WHERE"
                                + " r.id='00000000-0000-0000-0000-000000992112'"))
                .containsExactly("scm.pull_request_review|992119|true|scm.pull_request|992109|true");
        assertThat(column(
                        database,
                        "SELECT e.name || '|' || e.actor_user_id || '|' || (e.item_id=r.item_id) FROM"
                                + " item_event e JOIN review r ON r.id='00000000-0000-0000-0000-000000992112'"
                                + " WHERE e.id='00000000-0000-0000-0000-000000992181'"))
                .containsExactly("scm.pull_request_review.submitted|992104|true");
        assertThat(column(database, """
            SELECT e.state || '|' || (e.retry_at <= now()) || '|' || e.item_version || '|'
                || (e.last_attempted_at = '2026-01-02T00:01:00Z'::timestamptz) || '|'
                || (e.item_id = i.id) || '|' || e.actor_user_id
            FROM item_event e JOIN item i ON i.workspace_id=e.workspace_id
                AND i.kind='scm.pull_request' AND i.source_id=992109
            WHERE e.id='00000000-0000-0000-0000-000000992184'
            """)).containsExactly("WAITING|true|pending-head|true|true|992103");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreservePinnedDefinitionsAndTheirNativeIndexes(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM practice_version WHERE id=992131 AND"
                                + " definition->'reads'->>'contract'='1.3.0'"))
                .containsExactly("1");
        assertThat(column(
                        database,
                        "SELECT v.definition->'reads'->>'contract' FROM practice p JOIN practice_version v ON"
                                + " v.id=p.current_version_id WHERE p.id=992121"))
                .containsExactly("2.0.0");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE"
                                + " c.relname='uk_result_current' AND i.indisvalid AND i.indnullsnotdistinct"))
                .containsExactly("1");
        assertThat(
                        column(
                                database,
                                "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND"
                                    + " table_name IN"
                                    + " ('observation','practice_result','practice_observation','agent_job','artifact_signal','identity_link','workspace_membership')"))
                .containsExactly("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldEnforceTenantAndEvidenceConstraints(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThatThrownBy(() ->
                        execute(database, "DELETE FROM membership WHERE workspace_id=992101 AND account_id=992106"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("23001"));
        assertThatThrownBy(() -> execute(
                        database,
                        "INSERT INTO"
                            + " result(id,workspace_id,review_id,practice_id,practice_version_id,item_id,item_version,developer_id,outcome,summary,evidence_version,recorded_at)"
                            + " SELECT"
                            + " gen_random_uuid(),workspace_id,review_id,practice_id,practice_version_id,item_id,item_version,developer_id,outcome,summary,0,recorded_at"
                            + " FROM result WHERE id='"
                                + RESULT + "'"))
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("evidence version 1");
        assertThatThrownBy(() -> execute(
                        database,
                        "INSERT INTO"
                                + " problem(id,workspace_id,result_id,position,severity,summary,explanation,citations)"
                                + " VALUES(gen_random_uuid(),992201,'"
                                + RESULT + "',1,'MINOR','Other','Other','[]')"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("23503"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepAcceptedResultsCurrentWhenAnAcceptanceIsRemoved(String context) throws Exception {
        var database = migratedDatabase(context);
        execute(
                database,
                "INSERT INTO result_mark(id,workspace_id,result_id,kind,reason) VALUES(gen_random_uuid(),992101,'"
                        + RESULT + "','ACCEPTED','Accept this risk')");
        assertThat(column(database, "SELECT (ended_at IS NULL)::text FROM result WHERE id='" + RESULT + "'"))
                .containsExactly("true");
        execute(
                database,
                "UPDATE result_mark SET removed_at=now(),removal_reason='Reconsidered' WHERE result_id='" + RESULT
                        + "' AND kind='ACCEPTED'");
        assertThat(column(database, "SELECT (ended_at IS NULL)::text FROM result WHERE id='" + RESULT + "'"))
                .containsExactly("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldReplayWithoutChangingMigratedProductFacts(String context) throws Exception {
        var database = migratedDatabase(context);
        var beforeReplay = column(
                database,
                "SELECT (SELECT count(*) FROM result WHERE workspace_id=992101)::text || '|' || (SELECT count(*) FROM"
                    + " feedback_copy WHERE workspace_id=992101) || '|' || (SELECT count(*) FROM feedback_send WHERE"
                    + " workspace_id=992101)");
        upgrade(database, context);
        assertThat(column(
                        database,
                        "SELECT (SELECT count(*) FROM result WHERE workspace_id=992101)::text || '|' ||"
                                + " (SELECT count(*) FROM feedback_copy WHERE workspace_id=992101) || '|' ||"
                                + " (SELECT count(*) FROM feedback_send WHERE workspace_id=992101)"))
                .isEqualTo(beforeReplay);
        assertThat(column(database, "SELECT locked::text FROM databasechangeloglock WHERE id=1"))
                .containsExactly("false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldHaltAndRollBackWhenFeedbackPeopleDisagree(String context) throws Exception {
        var database = releasedDatabase(context);
        execute(database, "UPDATE feedback SET about_user_id=992104 WHERE id='" + FEEDBACK + "'");
        assertThatThrownBy(() -> upgrade(database, context)).hasMessageContaining("feedback person is ambiguous");
        assertThat(column(database, "SELECT count(*)::text FROM observation WHERE id='" + RESULT + "'"))
                .containsExactly("1");
        assertThat(column(
                        database,
                        "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND"
                                + " table_name='result'"))
                .containsExactly("0");
        assertThat(column(database, "SELECT locked::text FROM databasechangeloglock WHERE id=1"))
                .containsExactly("false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepPostedSendsFinishedAndConsentFactsImmutableWhenReleasedDataIsMigrated(String context)
            throws Exception {
        var database = releasedDatabase(context);
        upgrade(database, context);
        assertThat(column(
                        database,
                        "SELECT feedback_id::text || '|' || action || '|' || state || '|' || attempts FROM"
                            + " feedback_send WHERE feedback_id IN"
                            + " ('00000000-0000-0000-0000-000000992161','00000000-0000-0000-0000-000000992231','00000000-0000-0000-0000-000000992232')"
                            + " ORDER BY feedback_id"))
                .containsExactly(
                        "00000000-0000-0000-0000-000000992161|POST|DONE|2",
                        "00000000-0000-0000-0000-000000992231|POST|DONE|3",
                        "00000000-0000-0000-0000-000000992232|POST|DONE|4");
        assertThat(column(
                        database,
                        "SELECT feedback_id::text || '|' || action || '|' || state FROM feedback_send"
                                + " WHERE feedback_id BETWEEN '00000000-0000-0000-0000-000000992311'::uuid"
                                + " AND '00000000-0000-0000-0000-000000992316'::uuid ORDER BY feedback_id"))
                .containsExactly(
                        "00000000-0000-0000-0000-000000992311|POST|PENDING",
                        "00000000-0000-0000-0000-000000992312|POST|UNCERTAIN",
                        "00000000-0000-0000-0000-000000992313|POST|UNCERTAIN",
                        "00000000-0000-0000-0000-000000992314|POST|DONE",
                        "00000000-0000-0000-0000-000000992315|RETRACT|PENDING",
                        "00000000-0000-0000-0000-000000992316|POST|DONE");
        assertThat(column(
                        database,
                        "SELECT id::text || '|' || state || '|' || external_ref FROM feedback_copy"
                                + " WHERE id='00000000-0000-0000-0000-000000992334'"))
                .containsExactly("00000000-0000-0000-0000-000000992334|RETRACTED|provider:updated");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldWithholdSuppressedFeedbackWithoutCreatingAnUnattemptedCopy(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT state || '|' || stop_reason FROM feedback WHERE"
                                + " id='00000000-0000-0000-0000-000000992316'"))
                .containsExactly("WITHHELD|OUTSIDE_CURRENT_SCOPE");
        assertThat(column(
                        database,
                        "SELECT id::text FROM feedback_copy WHERE feedback_id='00000000-0000-0000-0000-000000992316'"))
                .isEmpty();
        assertThat(column(database, "SELECT state FROM feedback_copy WHERE id='00000000-0000-0000-0000-000000992332'"))
                .containsExactly("WRITE_STARTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreservePracticeStateFiltersAndWorkspaceReviewScope(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT review_scope_repositories || '|' || review_scope_people FROM workspace WHERE"
                                + " id=992101"))
                .containsExactly("ALL|ALL");
        assertThat(column(
                        database,
                        "SELECT definition->'reviewOn'->'state'->'state' FROM practice_version WHERE id=992131"))
                .containsExactly("[\"OPEN\"]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveImmutableConsentFacts(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT account_id || '|' || purpose || '|' || granted || '|' || mechanism || '|' ||"
                                + " notice_version FROM consent_decision WHERE id=992251"))
                .containsExactly("992106|PRIVACY_NOTICE_ACKNOWLEDGEMENT|true|ACCOUNT_SETTINGS|2026-08-30");
        assertThatThrownBy(() -> execute(database, "UPDATE consent_decision SET granted=false WHERE id=992251"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("55000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRejectInvalidReadbackWithoutChangingPostedCopies(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThatThrownBy(() -> execute(
                        database,
                        "UPDATE feedback_copy SET readback='NOT_A_READBACK' WHERE"
                                + " id='00000000-0000-0000-0000-000000992171'"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        assertThat(column(
                        database,
                        "SELECT state || '|' || external_ref FROM feedback_copy WHERE"
                                + " id='00000000-0000-0000-0000-000000992171'"))
                .containsExactly("POSTED|provider:123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveCompositionApprovalAndResponseHistory(String context) throws Exception {
        var database = migratedDatabase(context);
        assertThat(column(
                        database,
                        "SELECT admission_attempt || '|' || admission_digest || '|' || basis_digest FROM"
                                + " review_composition_basis WHERE job_id='00000000-0000-0000-0000-000000992111'"))
                .containsExactly("0|" + "a".repeat(64) + "|" + "b".repeat(64));
        assertThat(column(
                        database,
                        "SELECT actor_account_id::text FROM feedback_decision WHERE"
                                + " id='00000000-0000-0000-0000-000000992261'"))
                .containsExactly("992106");
        assertThat(column(
                        database,
                        "SELECT f.developer_id::text || '|' || r.resolution || '|' || r.usefulness FROM"
                                + " feedback_response r JOIN feedback f ON f.id=r.feedback_id AND"
                                + " f.workspace_id=r.workspace_id WHERE r.id='00000000-0000-0000-0000-000000992263'"))
                .containsExactly("992106|ADDRESSED|HELPFUL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRenameOnlyPersistedReviewAuditKeysAndRestoreTheAppendOnlyGuard(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        String facts = "SELECT (to_jsonb(e)-'entity_type')::text FROM config_audit_event e"
                + " WHERE id BETWEEN 995901 AND 995907 ORDER BY id";
        String guard = "SELECT pg_get_triggerdef(oid) || '|' || tgenabled::text FROM pg_trigger"
                + " WHERE tgrelid='config_audit_event'::regclass AND tgname='trg_config_audit_event_block_mutation'";
        var before = PracticeMigrationFixture.column(database, facts);
        var protection = PracticeMigrationFixture.column(database, guard);
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(database, facts)).isEqualTo(before);
        assertThat(PracticeMigrationFixture.column(database, guard)).isEqualTo(protection);
        assertThat(PracticeMigrationFixture.column(database, """
            SELECT id || '|' || entity_type FROM config_audit_event WHERE id BETWEEN 995901 AND 995907 ORDER BY id
            """))
                .containsExactly(
                        "995901|REVIEW_BATCH",
                        "995902|REVIEW_SCHEDULE",
                        "995903|PRACTICE_REVIEW_SETTINGS",
                        "995904|CATALOG_PRACTICE",
                        "995905|CATALOG_GROUP",
                        "995906|CATALOG_GROUP",
                        "995907|PRACTICE_GROUP");
        assertThat(PracticeMigrationFixture.column(database, """
            SELECT convalidated::text FROM pg_constraint WHERE conrelid='config_audit_event'::regclass
                AND conname IN ('ck_config_audit_event_entity_type','ck_config_audit_event_scope') ORDER BY conname
            """)).containsExactly("true", "true");
        if (context.equals("prod")) {
            assertThat(protection).hasSize(1);
            assertThatThrownBy(() -> PracticeMigrationFixture.execute(
                            database, "UPDATE config_audit_event SET entity_type='AGENT_BINDING' WHERE id=995901"))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "prod,CATALOG_PRACTICE,992101",
                "dev,CATALOG_PRACTICE,992101",
                "prod,WORKSPACE_ROLE,NULL",
                "dev,WORKSPACE_ROLE,NULL",
                "prod,UNKNOWN,992101",
                "dev,UNKNOWN,992101"
            },
            nullValues = "NULL")
    void shouldRejectAuditEntriesOutsideTheirDeclaredScope(
            String context, String entityType, @Nullable Long workspaceId) throws Exception {
        var database = migratedDatabase(context);
        String scope = workspaceId == null ? "NULL" : workspaceId.toString();
        assertThatThrownBy(() -> execute(database, """
            INSERT INTO config_audit_event(id,workspace_id,actor_kind,entity_type,entity_id,action,
                changed_keys,old_value,new_value,occurred_at)
            VALUES(995908,%s,'SYSTEM','%s','scope-check','CREATED',ARRAY['name'],'{}','{}',now())
            """.formatted(scope, entityType)))
                .rootCause()
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23514");
    }
}
