package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1CurrentResultMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRankRetainedOccasionsBeforeLateCompletionAndKeepMarksSeparate(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO agent_job(id,workspace_id,job_type,status,config_snapshot,job_token,retry_count,
                created_at,completed_at,available_at,trace_id,metadata,delivery_status,evidence_snapshot)
            SELECT ('00000000-0000-0000-0000-000000'||n)::uuid,992101,'PULL_REQUEST_REVIEW',
                'COMPLETED','{}','ordering-'||n,0,created,observed,created,repeat('d',32),
                jsonb_build_object('pull_request_id',992109,'about_user_id',992103,
                    'observation_origin',origin,'commit_sha','released-head',
                    'current_work_at_admission',true,'signal','scm.pull_request.opened'),
                'DELIVERED','{"reviewedWork":{"retainedBasis":"ADMISSION"}}'::jsonb
            FROM (VALUES
                (994111,'LIVE','2026-01-02'::timestamptz,'2026-01-10'::timestamptz),
                (994112,'LIVE','2026-01-03'::timestamptz,'2026-01-04'::timestamptz),
                (994113,'BACKFILL','2026-01-11'::timestamptz,'2026-01-11'::timestamptz),
                (994114,'MANUAL','2026-01-05'::timestamptz,'2026-01-05'::timestamptz),
                (994115,'LIVE','2026-01-06'::timestamptz,'2026-01-06'::timestamptz)
            ) fixture(n,origin,created,observed);
            INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,
                practice_revision_id,artifact_kind,artifact_id,about_user_id,summary,outcome,
                severity,evidence,evidence_rationale,observed_at,origin,superseded_at)
            SELECT ('00000000-0000-0000-0000-000000'||(n+100))::uuid,'ordering-'||n,
                ('00000000-0000-0000-0000-000000'||n)::uuid,992101,992121,992131,
                'scm.pull_request',992109,992103,'Explain the retry','NOT_MET','INFO',
                '{"citations":[]}','Retain the reason.',observed,origin,
                CASE WHEN n=994115 THEN '2026-01-07'::timestamptz END
            FROM (VALUES (994111,'LIVE','2026-01-10'::timestamptz),
                (994112,'LIVE','2026-01-04'::timestamptz),
                (994113,'BACKFILL','2026-01-11'::timestamptz),
                (994114,'MANUAL','2026-01-05'::timestamptz),
                (994115,'LIVE','2026-01-06'::timestamptz)) fixture(n,origin,observed);
            INSERT INTO observation_invalidation(id,workspace_id,observation_id,reason,invalidated_at,provider_copy,provider_copy_retry_at)
            VALUES('00000000-0000-0000-0000-000000994301',992101,
                '00000000-0000-0000-0000-000000994214','Wrong evidence','2026-01-05','NONE','2026-01-05');
            """);
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT id::text FROM result WHERE workspace_id=992101 AND ended_at IS NULL"))
                .containsExactly("00000000-0000-0000-0000-000000994214");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT end_reason FROM result WHERE id='00000000-0000-0000-0000-000000994213'"))
                .containsExactly("OUTRANKED");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT end_reason FROM result WHERE id='00000000-0000-0000-0000-000000994215'"))
                .containsExactly("WORK_CHANGED");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT count(*)::text FROM result WHERE workspace_id=992101 AND ended_at<recorded_at"))
                .containsExactly("0");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT kind || '|' || (removed_at IS NULL) FROM result_mark WHERE"
                                + " id='00000000-0000-0000-0000-000000994301'"))
                .containsExactly("INCORRECT|true");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT ordered_at::date::text FROM review WHERE id='00000000-0000-0000-0000-000000994111'"))
                .containsExactly("2026-01-02");
    }
}
