package de.tum.cit.aet.hephaestus.core.database;

import static org.assertj.core.api.Assertions.assertThat;

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
}
