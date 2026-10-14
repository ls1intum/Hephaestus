package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1PeopleMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRetainMainOwnedAuditAndProviderDefinitions(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        String definition = """
            SELECT value FROM (
              SELECT 'column|' || table_name || '|' || column_name || '|' || data_type || '|' ||
                is_nullable || '|' || coalesce(column_default,'') AS value
              FROM information_schema.columns WHERE table_schema='public'
                AND table_name IN ('config_audit_event','connection_audit','project_field')
              UNION ALL
              SELECT 'constraint|' || c.relname || '|' || k.conname || '|' || pg_get_constraintdef(k.oid)
              FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid WHERE c.relnamespace='public'::regnamespace
                AND c.relname IN ('config_audit_event','connection_audit','project_field') AND k.conname <> 'ck_config_audit_event_entity_type'
              UNION ALL
              SELECT 'index|' || tablename || '|' || indexname || '|' || indexdef
              FROM pg_indexes WHERE schemaname='public'
                AND tablename IN ('config_audit_event','connection_audit','project_field')
            ) source ORDER BY value
            """;
        var before = PracticeV1CutoverMigrationTest.column(database, definition);
        assertThat(before).isNotEmpty();
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        assertThat(PracticeV1CutoverMigrationTest.column(database, definition)).isEqualTo(before);
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE"
                                + " conname='ck_config_audit_event_entity_type'"))
                .containsExactly("""
                    CHECK (((entity_type)::text = ANY (ARRAY[('PRACTICE_REVIEW_SETTINGS'::character varying)::text, ('AGENT_BINDING'::character varying)::text, ('AGENT_CONFIG'::character varying)::text, ('AI_CONFIG_BINDING'::character varying)::text, ('WORKSPACE_ROLE'::character varying)::text, ('ACTIVITY_AUTOMATION'::character varying)::text, ('WORKSPACE_FEATURES'::character varying)::text, ('WORKSPACE_STATUS'::character varying)::text, ('WORKSPACE_TOKEN'::character varying)::text, ('WORKSPACE_VISIBILITY'::character varying)::text, ('PRACTICE_ACTIVE'::character varying)::text, ('PRACTICE_USAGE'::character varying)::text, ('PRACTICE_DEFINITION'::character varying)::text, ('PRACTICE_GROUP'::character varying)::text, ('PRACTICE_AREA'::character varying)::text, ('CATALOG_PRACTICE'::character varying)::text, ('CATALOG_GROUP'::character varying)::text, ('CURATED_PRACTICE_AREA'::character varying)::text, ('WORKSPACE_INSTANCE_LLM_BUDGET'::character varying)::text, ('WORKSPACE_OWN_PROVIDER_LLM_BUDGET'::character varying)::text, ('WORKSPACE_LLM_BUDGET'::character varying)::text, ('WORKSPACE_BYO_LLM_BUDGET'::character varying)::text, ('REVIEW_BATCH'::character varying)::text, ('REVIEW_SCHEDULE'::character varying)::text, ('WORKSPACE_LLM_CONNECTION'::character varying)::text, ('WORKSPACE_LLM_MODEL'::character varying)::text])))\
                    """.strip());
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT table_name || '|' || column_name || '|' || data_type FROM information_schema.columns
            WHERE table_schema='public' AND (table_name,column_name) IN
              (('auth_event','details'),('config_audit_event','old_value'),('config_audit_event','new_value'),
               ('connection_audit','detail'),('project_field','options')) ORDER BY table_name,column_name
            """))
                .containsExactly(
                        "auth_event|details|text",
                        "config_audit_event|new_value|text",
                        "config_audit_event|old_value|text",
                        "connection_audit|detail|text",
                        "project_field|options|text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldPreserveExactIdentityAuthorityAndConservativePersonChoices(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO identity_provider(id,type,server_url) VALUES(999102,'GITLAB','https://gitlab.practice-v1.example'),
                (999112,'SLACK','https://slack.practice-v1.example');
            INSERT INTO "user"(id,native_id,provider_id,login,type) VALUES(999103,103,999102,'same-person','USER'),
                (999104,104,999102,'former-person','USER');
            INSERT INTO identity_link(id,account_id,provider_id,subject,linked_via,linked_at,last_login_at,disabled_at)
            VALUES(999107,992106,999102,'103','MANUAL_LINK','2026-01-01','2026-01-02','2026-01-03');
            INSERT INTO workspace_membership(workspace_id,user_id,role,hidden,created_at) VALUES(992101,999103,'ADMIN',true,'2026-01-01');
            INSERT INTO user_preferences(id,user_id,ai_review_enabled) VALUES(999108,992103,true),(999109,999103,false);
            INSERT INTO workspace_hidden_former_member(workspace_id,user_id) VALUES(992101,999104);
            INSERT INTO account(id,display_name,status,app_role) VALUES(999106,'Onboarding only','ACTIVE','USER');
            INSERT INTO workspace_member_onboarding(id,workspace_id,account_id,seen_revision,public_activity_seen,updated_at)
            VALUES(999110,992201,999106,7,true,'2026-01-01');
            INSERT INTO identity_link(id,account_id,provider_id,subject,team_id,linked_via)
            VALUES(999117,992106,999112,'U1','T1','OAUTH_LOGIN'),(999118,999106,999112,'U1','T2','OAUTH_LOGIN');
            INSERT INTO public_activity_objection(id,provider_id,subject)
            VALUES('00000000-0000-0000-0000-000000999121',992102,'103');
            INSERT INTO person_data_request(id,state,counts_json,completed_json,created_at,expires_at,version)
            VALUES('00000000-0000-0000-0000-000000999122','PREVIEW','{}','{}','2026-01-01','2027-01-01',0);
            INSERT INTO person_suppression(id,provider_id,subject,team_key,active_request_id)
            VALUES('00000000-0000-0000-0000-000000999123',999112,'U9','T3','00000000-0000-0000-0000-000000999122');
            INSERT INTO auth_event(id,occurred_at,account_id,event_type,result,workspace_id,identity_link_id,
                viewed_user_id,ip_inet,user_agent,details)
            VALUES(999131,'2026-01-01',992106,'USER_VIEW','SUCCESS',992101,992107,992103,
                '192.0.2.1','Exact browser','{"exact": true}');
            """);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT ai_choice || '|' || (ai_choice_at='2026-01-01T00:00:00Z'::timestamptz) || '|' ||"
                                + " feedback_delivery_enabled || '|' || public_activity_visible FROM account WHERE"
                                + " id=992106"))
                .containsExactly("IN_HOUSE_ONLY|true|false|false");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT role || '|' || hidden || '|' || (left_at IS NULL) FROM membership WHERE"
                                + " workspace_id=992101 AND account_id=992106"))
                .containsExactly("OWNER|true|true");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT id || '|' || linked_via || '|' || (disabled_at='2026-01-03'::timestamptz) FROM"
                                + " identity WHERE id=999107"))
                .containsExactly("999107|MANUAL|true");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT provider_user_key || '|' || team_key || '|' || account_id FROM identity WHERE"
                                + " id IN(999117,999118) ORDER BY id"))
                .containsExactly("U1|T1|992106", "U1|T2|999106");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT a.status || '|' || (a.primary_email IS NULL) || '|' || a.app_role || '|' ||"
                                + " m.hidden || '|' || (m.left_at IS NOT NULL) FROM identity i JOIN account a ON"
                                + " a.id=i.account_id JOIN membership m ON m.account_id=a.id WHERE"
                                + " i.provider_id=999102 AND i.provider_user_key='104'"))
                .containsExactly("UNCLAIMED|true|USER|true|true");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT (left_at IS NOT NULL)::text || '|' || onboarding_seen_revision || '|' ||"
                                + " public_activity_seen FROM membership WHERE workspace_id=992201 AND"
                                + " account_id=999106"))
                .containsExactly("true|7|true");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT kind || '|' || provider_user_key || '|' || team_key || '|' ||"
                                + " active_request_id FROM identity_restriction WHERE"
                                + " id='00000000-0000-0000-0000-000000999123'"))
                .containsExactly("ERASED|U9|T3|00000000-0000-0000-0000-000000999122");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT viewed_account_id || '|' || viewed_actor_id || '|' || identity_id || '|' ||"
                                + " host(ip_inet) || '|' || user_agent || '|' || details || '|' ||"
                                + " occurred_at::date FROM auth_event WHERE id=999131"))
                .containsExactly("992106|992103|992107|192.0.2.1|Exact browser|{\"exact\": true}|2026-01-01");
        assertThat(PracticeV1CutoverMigrationTest.column(
                        database,
                        "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND"
                                + " table_name='auth_event' AND column_name='details'"))
                .containsExactly("text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldRenameOnlyPersistedReviewAuditKeysAndRestoreTheAppendOnlyGuard(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO config_audit_event(id,workspace_id,actor_kind,actor_account_id,acting_account_id,
                entity_type,entity_id,action,changed_keys,old_value,new_value,occurred_at)
            VALUES(995901,992101,'SYSTEM',NULL,NULL,'REVIEW_BACKFILL_RUN','original-batch','CREATED',
                    ARRAY['lookback'],'{"before":1}','{"after":2}','2026-01-01T00:00:00Z'),
                  (995902,992101,'SYSTEM',NULL,NULL,'REVIEW_SWEEP_SCHEDULE','original-schedule','UPDATED',
                    ARRAY['paused'],'{"before":false}','{"after":true}','2026-01-02T00:00:00Z'),
                  (995903,992101,'SYSTEM',NULL,NULL,'PRACTICE_REVIEW_SETTINGS','original-settings','UPDATED',
                    ARRAY['mode'],'{"before":"kept"}','{"after":"kept"}','2026-01-03T00:00:00Z');
            """);
        String facts = "SELECT (to_jsonb(e)-'entity_type')::text FROM config_audit_event e"
                + " WHERE id IN(995901,995902,995903) ORDER BY id";
        String guard = "SELECT pg_get_triggerdef(oid) || '|' || tgenabled::text FROM pg_trigger"
                + " WHERE tgrelid='config_audit_event'::regclass AND tgname='trg_config_audit_event_block_mutation'";
        var before = PracticeV1CutoverMigrationTest.column(database, facts);
        var protection = PracticeV1CutoverMigrationTest.column(database, guard);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        assertThat(PracticeV1CutoverMigrationTest.column(database, facts)).isEqualTo(before);
        assertThat(PracticeV1CutoverMigrationTest.column(database, guard)).isEqualTo(protection);
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT id || '|' || entity_type FROM config_audit_event WHERE id IN(995901,995902,995903) ORDER BY id
            """))
                .containsExactly("995901|REVIEW_BATCH", "995902|REVIEW_SCHEDULE", "995903|PRACTICE_REVIEW_SETTINGS");
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT convalidated::text FROM pg_constraint WHERE conrelid='config_audit_event'::regclass
                AND conname='ck_config_audit_event_entity_type'
            """)).containsExactly("true");
        if (context.equals("prod")) {
            assertThat(protection).hasSize(1);
            assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.execute(
                            database, "UPDATE config_audit_event SET entity_type='AGENT_BINDING' WHERE id=995901"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
        }
    }
}
