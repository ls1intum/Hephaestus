package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("database")
class PracticeV1MigrationPreconditionTest {
    private record Corruption(String seed, String reason, String repair) {}

    private static Stream<Arguments> corruptions() {
        var cases = new Corruption[] {
            new Corruption(
                    "UPDATE feedback_dispatch SET"
                            + " state='CLAIMED',lease_owner='released-worker',lease_expires_at=now()+interval '1 minute'"
                            + " WHERE id='00000000-0000-0000-0000-000000992241'",
                    "SQL Precondition failed",
                    "UPDATE feedback_dispatch SET state='PENDING',lease_owner=NULL,lease_expires_at=NULL WHERE"
                            + " id='00000000-0000-0000-0000-000000992241'"),
            new Corruption(
                    "UPDATE agent_job SET metadata=jsonb_set(metadata,'{about_user_id}','123456789'::jsonb) WHERE"
                            + " id='00000000-0000-0000-0000-000000992113'",
                    "person reference has no SCM user",
                    "UPDATE agent_job SET metadata=jsonb_set(metadata,'{about_user_id}','992103'::jsonb) WHERE"
                            + " id='00000000-0000-0000-0000-000000992113'"),
            new Corruption(
                    "UPDATE artifact_signal SET revision='event~unknown' WHERE"
                            + " id='00000000-0000-0000-0000-000000992181'",
                    "reviewer event has no exact pull request review id",
                    "UPDATE artifact_signal SET revision='event~992119' WHERE"
                            + " id='00000000-0000-0000-0000-000000992181'"),
            new Corruption(
                    "UPDATE artifact_signal SET revision='sha~'||repeat('a',40) WHERE"
                            + " id='00000000-0000-0000-0000-000000992181'",
                    "reviewer event has no exact pull request review id",
                    "UPDATE artifact_signal SET revision='event~992119' WHERE"
                            + " id='00000000-0000-0000-0000-000000992181'"),
            new Corruption(
                    "UPDATE artifact_signal SET revision='sha~not-a-head' WHERE"
                            + " id='00000000-0000-0000-0000-000000992186'",
                    "reviewer event has no exact pull request review id",
                    "UPDATE artifact_signal SET revision='sha~'||repeat('2',40) WHERE"
                            + " id='00000000-0000-0000-0000-000000992186'"),
            new Corruption(
                    "UPDATE artifact_signal SET artifact_kind='scm.issue' WHERE"
                            + " id='00000000-0000-0000-0000-000000992186'",
                    "reviewer event has no exact pull request review id",
                    "UPDATE artifact_signal SET artifact_kind='scm.pull_request' WHERE"
                            + " id='00000000-0000-0000-0000-000000992186'"),
            new Corruption(
                    "INSERT INTO issue(id,native_id,provider_id,issue_type,number,repository_id,title,comments_count,"
                            + "is_locked,created_at) VALUES(992790,790,992702,'PULL_REQUEST',1,992705,'GitLab change',0,"
                            + "false,'2026-01-01T00:00:00Z');"
                            + " INSERT INTO artifact_signal(id,workspace_id,artifact_kind,artifact_id,signal_name,revision,"
                            + "occurred_at,discovered_via,state,state_reason,state_changed_at)"
                            + " VALUES('00000000-0000-0000-0000-000000992790',992201,'scm.issue',992790,'scm.issue.opened',"
                            + "'dig~gitlab','2026-01-01T00:00:00Z','EVENT','SUPPRESSED','GATE_SKIPPED','2026-01-01T00:00:01Z')",
                    "item kind disagrees with provider mirror",
                    "DELETE FROM artifact_signal WHERE id='00000000-0000-0000-0000-000000992790';"
                            + " DELETE FROM issue WHERE id=992790"),
            new Corruption(
                    "INSERT INTO issue(id,native_id,provider_id,issue_type,number,repository_id,title,comments_count,"
                            + "is_locked,created_at) VALUES(992791,791,992102,'ISSUE',2,992108,'Plain issue',0,"
                            + "false,'2026-01-01T00:00:00Z');"
                            + " INSERT INTO artifact_signal(id,workspace_id,artifact_kind,artifact_id,signal_name,revision,"
                            + "occurred_at,discovered_via,state,state_reason,state_changed_at)"
                            + " VALUES('00000000-0000-0000-0000-000000992791',992101,'scm.pull_request',992791,"
                            + "'scm.pull_request.opened','sha~'||repeat('c',40),'2026-01-01T00:00:00Z','EVENT','SUPPRESSED',"
                            + "'GATE_SKIPPED','2026-01-01T00:00:01Z')",
                    "item kind disagrees with provider mirror",
                    "DELETE FROM artifact_signal WHERE id='00000000-0000-0000-0000-000000992791';"
                            + " DELETE FROM issue WHERE id=992791"),
            new Corruption(
                    "UPDATE artifact_signal SET state='RECORDED',state_reason=NULL WHERE"
                            + " id='00000000-0000-0000-0000-000000992187'",
                    "item kind disagrees with provider mirror",
                    "UPDATE artifact_signal SET state='SUPPRESSED',state_reason='GATE_SKIPPED' WHERE"
                            + " id='00000000-0000-0000-0000-000000992187'"),
            new Corruption(
                    "UPDATE artifact_signal SET job_id='00000000-0000-0000-0000-000000992112' WHERE"
                            + " id='00000000-0000-0000-0000-000000992188'",
                    "item kind disagrees with provider mirror",
                    "UPDATE artifact_signal SET job_id=NULL WHERE id='00000000-0000-0000-0000-000000992188'"),
            new Corruption(
                    "INSERT INTO review_backfill_run(id,workspace_id,artifact_kind,from_at,to_at,status,"
                            + "estimated_artifacts,requested_by_account_id,cursor_artifact_id,created_at,updated_at)"
                            + " VALUES('00000000-0000-0000-0000-000000992274',992101,'scm.issue','2026-01-01T00:00:00Z',"
                            + "'2026-01-02T00:00:00Z','CANCELLED',1,992106,992109,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')",
                    "item kind disagrees with provider mirror",
                    "DELETE FROM review_backfill_run WHERE id='00000000-0000-0000-0000-000000992274'"),
            new Corruption(
                    "UPDATE feedback_dispatch SET body='Different text.' WHERE"
                            + " id='00000000-0000-0000-0000-000000992359'",
                    "approved summary text differs from its released proposal",
                    "UPDATE feedback_dispatch SET body='' WHERE id='00000000-0000-0000-0000-000000992359'"),
            new Corruption(
                    "UPDATE feedback_dispatch SET delivered_placements=jsonb_set(delivered_placements,'{1,deliveryKey}',"
                            + "'\"approved:00000000-0000-0000-0000-000000992320:0\"') WHERE"
                            + " id='00000000-0000-0000-0000-000000992360'",
                    "ambiguous physical copy reconciliation",
                    "UPDATE feedback_dispatch SET delivered_placements=jsonb_set(delivered_placements,'{1,deliveryKey}',"
                            + "'\"approved:00000000-0000-0000-0000-000000992320:1\"') WHERE"
                            + " id='00000000-0000-0000-0000-000000992360'"),
            new Corruption(
                    "UPDATE feedback SET proposed_placements='[{\"type\":\"LOCATION_COMMENT\",\"body\":\"Check the timeout.\","
                            + "\"path\":\"src/retry.ts\",\"startLine\":1,\"endLine\":1}]' WHERE"
                            + " id='00000000-0000-0000-0000-000000992320'",
                    "ambiguous physical copy reconciliation",
                    "UPDATE feedback SET proposed_placements='[]' WHERE id='00000000-0000-0000-0000-000000992320'"),
            new Corruption(
                    "INSERT INTO person_suppression(id,provider_id,subject,team_key)"
                            + " VALUES('00000000-0000-0000-0000-000000999999',992102,'103','')",
                    "person-owned data remains for an erased identity",
                    "DELETE FROM person_suppression WHERE id='00000000-0000-0000-0000-000000999999'"),
            new Corruption(
                    "UPDATE feedback SET delivery_state='PARTIALLY_FAILED' WHERE"
                            + " id='00000000-0000-0000-0000-000000992312'",
                    "partial delivery has no confirmed appearance",
                    "UPDATE feedback SET delivery_state='UNCONFIRMED' WHERE"
                            + " id='00000000-0000-0000-0000-000000992312'"),
            new Corruption(
                    "UPDATE observation SET evidence=jsonb_set(evidence,'{citations}','{}'::jsonb) WHERE"
                            + " id='00000000-0000-0000-0000-000000992141'",
                    "invalid released citations",
                    "UPDATE observation SET evidence=jsonb_set(evidence,'{citations}','[]'::jsonb) WHERE"
                            + " id='00000000-0000-0000-0000-000000992141'"),
            new Corruption(
                    "UPDATE observation SET"
                            + " evidence=jsonb_set(evidence,'{citations,0,commit}',to_jsonb(repeat('b',40))) WHERE"
                            + " id='00000000-0000-0000-0000-000000992141'",
                    "released citation commits conflict",
                    "UPDATE observation SET evidence=evidence #- '{citations,0,commit}'"
                            + " WHERE id='00000000-0000-0000-0000-000000992141'")
        };
        return Stream.of("prod", "dev")
                .flatMap(context -> Arrays.stream(cases).map(corruption -> Arguments.of(context, corruption)));
    }

    @ParameterizedTest
    @MethodSource("corruptions")
    void shouldHaltAtomicallyAndPermitTheCorrectedSourceToMigrate(String context, Corruption corruption)
            throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.execute(database, corruption.seed());
        String auditFacts = "SELECT to_jsonb(e)::text FROM config_audit_event e"
                + " WHERE id BETWEEN 995901 AND 995907 ORDER BY id";
        String auditGuard = "SELECT tgenabled::text FROM pg_trigger"
                + " WHERE tgrelid='config_audit_event'::regclass AND tgname='trg_config_audit_event_block_mutation'";
        var sourceAudit = PracticeMigrationFixture.column(database, auditFacts);
        var sourceGuard = PracticeMigrationFixture.column(database, auditGuard);
        assertThatThrownBy(() -> PracticeMigrationFixture.upgrade(database, context))
                .hasMessageContaining(corruption.reason());
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT count(*)::text FROM account WHERE status='UNCLAIMED'"))
                .containsExactly("0");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public'"
                                + " AND table_name IN ('item','result','review_job')"))
                .containsExactly("0");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT delivery_state FROM feedback WHERE id='00000000-0000-0000-0000-000000992161'"))
                .containsExactly("DELIVERED");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT locked::text FROM databasechangeloglock WHERE id=1"))
                .containsExactly("false");
        assertThat(PracticeMigrationFixture.column(database, auditFacts)).isEqualTo(sourceAudit);
        assertThat(PracticeMigrationFixture.column(database, auditGuard)).isEqualTo(sourceGuard);
        PracticeMigrationFixture.execute(database, corruption.repair());
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT external_ref FROM feedback_copy WHERE id='00000000-0000-0000-0000-000000992171'"))
                .containsExactly("provider:123");
    }
}
