package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1ItemMappingMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldCreateSeparateWorkspaceItemsForSharedMirrorsAndMapAdapterPeople(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO workspace_membership(workspace_id,user_id,role,hidden,created_at) VALUES(992201,992103,'MEMBER',false,'2026-01-01');
            INSERT INTO practice(id,workspace_id,slug,name,criteria,applies_to,subject,signals,evidence_requirements,
                review_when,automated_review_policy,delivery_behavior,created_at)
            SELECT 998121,992201,slug,name,criteria,applies_to,subject,signals,evidence_requirements,
                review_when,automated_review_policy,delivery_behavior,created_at FROM practice WHERE id=992121;
            INSERT INTO practice_revision(id,practice_id,revision_number,slug,name,applies_to,subject,signals,
                evidence_requirements,review_when,criteria,automated_review_policy,delivery_behavior,created_at)
            SELECT 998131,id,1,slug,name,applies_to,subject,signals,evidence_requirements,review_when,
                criteria,automated_review_policy,delivery_behavior,'2026-01-01' FROM practice WHERE id=998121;
            UPDATE practice SET current_revision_id=998131 WHERE id=998121;
            INSERT INTO agent_job(id,workspace_id,job_type,status,config_snapshot,job_token,retry_count,
                created_at,completed_at,available_at,trace_id,metadata,delivery_status)
            VALUES('00000000-0000-0000-0000-000000998111',992201,'PULL_REQUEST_REVIEW','COMPLETED','{}',
                'shared-provider-work',0,'2026-01-01','2026-01-01T00:01:00Z','2026-01-01',repeat('e',32),
                '{"pull_request_id":992109,"about_user_id":992103,"commit_sha":"released-head"}','DELIVERED');
            INSERT INTO observation(id,occurrence_key,agent_job_id,workspace_id,practice_id,practice_revision_id,
                artifact_kind,artifact_id,about_user_id,summary,outcome,evidence,observed_at)
            VALUES('00000000-0000-0000-0000-000000998141','shared-provider-work',
                '00000000-0000-0000-0000-000000998111',992201,998121,998131,'scm.pull_request',992109,
                992103,'Purpose is clear','MET','{}','2026-01-01T00:00:30Z');
            INSERT INTO slack_thread(id,workspace_id,slack_channel_id,slack_thread_ts,participant_member_ids)
            VALUES(998201,992101,'C1','1.0','{992103,992104,992104}');
            INSERT INTO slack_message(id,workspace_id,slack_team_id,slack_channel_id,slack_ts,slack_thread_ts,
                author_slack_user_id,author_member_id,text)
            VALUES(998202,992101,'T1','C1','2.0','1.0','U1',992103,'Exact source words.');
            INSERT INTO slack_channel_consent_event(id,workspace_id,slack_channel_id,to_state,actor_user_id)
            VALUES(998203,992101,'C1','ACTIVE',992103);
            INSERT INTO connection(id,workspace_id,kind,state) VALUES(998211,992101,'OUTLINE','ACTIVE');
            INSERT INTO outline_document(id,workspace_id,connection_id,document_id,collection_id,body_markdown,updated_at)
            VALUES(998212,992101,998211,'document-1','collection-1','Exact document words.','2026-01-01');
            """);
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT count(DISTINCT id)::text || '|' || count(DISTINCT workspace_id) FROM item"
                                + " WHERE kind='scm.pull_request' AND source_id=992109"))
                .containsExactly("2|2");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT i.workspace_id || '|' || parent.workspace_id || '|' || parent.source_id FROM"
                                + " item i JOIN item parent ON parent.id=i.parent_id WHERE"
                                + " i.kind='scm.pull_request' AND i.source_id=992109 ORDER BY i.workspace_id"))
                .containsExactly("992101|992101|992108", "992201|992201|992108");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT workspace_id || '|' || developer_id || '|' || evidence_version || '|' ||"
                                + " (coverage IS NULL) FROM result WHERE"
                                + " id='00000000-0000-0000-0000-000000998141'"))
                .containsExactly("992201|992106|0|true");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT kind || '|' || workspace_id || '|' || (removed_at IS NULL) FROM item WHERE"
                                + " source_id IN (998201,998212) ORDER BY kind"))
                .containsExactly("chat.thread|992101|true", "docs.document|992101|true");
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT cardinality(participant_account_ids)::text || '|' ||"
                                + " (992106=ANY(participant_account_ids)) FROM slack_thread WHERE id=998201"))
                .containsExactly("2|true");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT author_account_id || '|' || text FROM slack_message WHERE id=998202"))
                .containsExactly("992106|Exact source words.");
        assertThat(PracticeMigrationFixture.column(
                        database, "SELECT actor_account_id::text FROM slack_channel_consent_event WHERE id=998203"))
                .containsExactly("992106");
        assertThat(PracticeMigrationFixture.column(database, "SELECT author_id::text FROM issue WHERE id=992109"))
                .containsExactly("992103");
    }
}
