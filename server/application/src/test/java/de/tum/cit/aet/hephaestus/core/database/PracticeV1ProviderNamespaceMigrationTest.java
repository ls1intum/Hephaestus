package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1ProviderNamespaceMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void shouldPreserveReleasedProviderControlsAcrossTheirDistinctNamespaces(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO identity_provider(id,type,server_url)
            VALUES(999901,'OUTLINE','https://docs.namespace.example:443/'),
                  (999905,'OUTLINE','https://docs.namespace.example'),
                  (999906,'SLACK','https://slack.com');
            INSERT INTO person_suppression(id,provider_id,subject,team_key)
            VALUES('00000000-0000-0000-0000-000000999902',999901,'person-1',''),
                  ('00000000-0000-0000-0000-000000999907',999905,'person-1','');
            INSERT INTO connection(id,workspace_id,kind,config)
            VALUES(999903,992101,'OUTLINE','{"serverUrl":"https://docs.namespace.example"}'),
                  (999904,992101,'SLACK','{}');
            """);

        String inherited = """
            SELECT p.server_url || '|' || s.subject || '|' || s.team_key
            FROM person_suppression s JOIN identity_provider p ON p.id=s.provider_id
            WHERE p.type='OUTLINE' AND p.server_url IN
                ('https://docs.namespace.example','https://docs.namespace.example:443/')
            ORDER BY p.server_url
            """;
        assertThat(PracticeMigrationFixture.column(database, inherited))
                .containsExactly(
                        "https://docs.namespace.example|person-1|", "https://docs.namespace.example:443/|person-1|");
        assertThat(PracticeMigrationFixture.column(database, """
            SELECT count(*)::text FROM identity_provider
            WHERE type='SLACK' AND server_url='https://slack.com'
            """)).containsExactly("1");
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(database, """
            SELECT p.server_url || '|' || r.provider_user_key || '|' || r.team_key || '|' || r.kind
            FROM identity_restriction r JOIN identity_provider p ON p.id=r.provider_id
            WHERE p.type='OUTLINE' AND p.server_url IN
                ('https://docs.namespace.example','https://docs.namespace.example:443/')
            ORDER BY p.server_url
            """))
                .containsExactly(
                        "https://docs.namespace.example|person-1||ERASED",
                        "https://docs.namespace.example:443/|person-1||ERASED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void shouldRemoveGuessedGitLabApprovalHeadsAndRetainGitHubApprovedCommits(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO identity_provider(id,type,server_url)
            VALUES(999920,'GITLAB','https://gitlab.approval.example');
            INSERT INTO repository(id,native_id,provider_id,name,name_with_owner,is_archived,is_disabled,is_private,has_discussions_enabled)
            VALUES(999921,421,999920,'project','approval/project',false,false,false,false);
            INSERT INTO issue(id,native_id,provider_id,issue_type,number,repository_id,title,comments_count,is_locked,created_at)
            VALUES(999922,422,999920,'PULL_REQUEST',1,999921,'Explain approval',0,false,'2026-01-01');
            INSERT INTO pull_request_review(id,native_id,provider_id,pull_request_id,state,is_dismissed,commit_id,body,submitted_at)
            VALUES(999923,423,999920,999922,'APPROVED',false,repeat('a',40),'Reviewed the change.','2026-01-02'),
                  (999924,424,992102,992109,'APPROVED',false,repeat('b',40),'Reviewed the change.','2026-01-02');
            """);
        String facts = "SELECT (to_jsonb(r)-'commit_id')::text FROM pull_request_review r"
                + " WHERE id IN (999923,999924) ORDER BY id";
        var before = PracticeMigrationFixture.column(database, facts);
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(database, facts)).isEqualTo(before);
        assertThat(PracticeMigrationFixture.column(database, """
            SELECT id || '|' || coalesce(commit_id,'unknown') FROM pull_request_review
            WHERE id IN (999923,999924) ORDER BY id
            """))
                .containsExactly("999923|unknown", "999924|" + "b".repeat(40));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void shouldPreserveReleasedProtocolsAndPermitAllCurrentLlmWireProtocols(String context) throws Exception {
        var database = PracticeMigrationFixture.releasedDatabase(context);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO llm_connection(id,slug,display_name,base_url,api_protocol,created_at)
            VALUES(999931,'released-chat','Released chat','https://llm.example','openai-completions','2026-01-01'),
                  (999932,'released-responses','Released responses','https://llm.example','openai-responses','2026-01-01');
            """);
        String facts = "SELECT to_jsonb(c)::text FROM llm_connection c WHERE id IN (999931,999932) ORDER BY id";
        var before = PracticeMigrationFixture.column(database, facts);
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(database, facts)).isEqualTo(before);
        PracticeMigrationFixture.execute(database, """
            INSERT INTO llm_connection(id,slug,display_name,base_url,api_protocol,created_at)
            VALUES(999933,'native-decisions','Decisions','https://llm.example','openai-decisions',now()),
                  (999934,'native-embeddings','Embeddings','https://llm.example','openai-embeddings',now()),
                  (999935,'native-rerank','Rerank','https://llm.example','cohere-rerank',now());
            """);
        assertThat(PracticeMigrationFixture.column(database, """
            SELECT api_protocol FROM llm_connection WHERE id BETWEEN 999931 AND 999935 ORDER BY id
            """))
                .containsExactly(
                        "openai-completions",
                        "openai-responses",
                        "openai-decisions",
                        "openai-embeddings",
                        "cohere-rerank");
        assertThatThrownBy(() -> PracticeMigrationFixture.execute(database, """
            UPDATE llm_connection SET api_protocol='unsupported' WHERE id=999933
            """))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23514"));
    }
}
