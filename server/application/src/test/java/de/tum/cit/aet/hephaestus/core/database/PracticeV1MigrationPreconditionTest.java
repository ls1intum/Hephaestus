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
                    "INSERT INTO person_suppression(id,provider_id,subject,team_key)"
                            + " VALUES('00000000-0000-0000-0000-000000999999',992102,'103','')",
                    "person-owned data remains for an erased identity",
                    "DELETE FROM person_suppression WHERE id='00000000-0000-0000-0000-000000999999'"),
            new Corruption(
                    "UPDATE observation SET evidence=jsonb_set(evidence,'{citations}','{}'::jsonb) WHERE"
                            + " id='00000000-0000-0000-0000-000000992141'",
                    "invalid released citations",
                    "UPDATE observation SET evidence=jsonb_set(evidence,'{citations}','[]'::jsonb) WHERE"
                            + " id='00000000-0000-0000-0000-000000992141'"),
            new Corruption(
                    "UPDATE observation SET evidence=jsonb_set(evidence,'{citations,0,commit}',to_jsonb(repeat('b',40)))"
                            + " WHERE id='00000000-0000-0000-0000-000000992141'",
                    "legacy citation commits conflict",
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
        PracticeMigrationFixture.execute(database, corruption.repair());
        PracticeMigrationFixture.upgrade(database, context);
        assertThat(PracticeMigrationFixture.column(
                        database,
                        "SELECT external_ref FROM feedback_copy WHERE id='00000000-0000-0000-0000-000000992171'"))
                .containsExactly("provider:123");
    }
}
