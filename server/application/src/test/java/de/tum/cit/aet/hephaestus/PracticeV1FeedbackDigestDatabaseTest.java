package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("database")
class PracticeV1FeedbackDigestDatabaseTest {
    private static final String WORK = "00000000-0000-0000-0000-000000992161";
    private static final String CHAT = "00000000-0000-0000-0000-000000992193";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepApprovalThroughFallbackAndBindThePreparedWordsAndSide(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,body,path,start_line,end_line,side,state,write_key,write_marker)
            VALUES('00000000-0000-0000-0000-000000997171',992101,'00000000-0000-0000-0000-000000992161',
                'LINE_NOTE',1,'Explain the retry.','retry.ts',10,12,'OLD','PLANNED',
                '00000000-0000-0000-0000-000000997171','<!-- hephaestus-feedback-copy:00000000-0000-0000-0000-000000997171 -->');
            """);
        var approved =
                PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + WORK + "')");
        PracticeV1CutoverMigrationTest.execute(database, """
            UPDATE feedback_copy SET kind='FILE_NOTE',state='POSTED',write_started_at=now(),posted_at=now(),external_ref='provider:456'
            WHERE id='00000000-0000-0000-0000-000000997171';
            """);
        assertThat(PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + WORK + "')"))
                .isEqualTo(approved);
        // These changes occur before a fence; admitted receipt facts remain immutable.
        PracticeV1CutoverMigrationTest.execute(database, """
            INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,body,path,start_line,end_line,side,state,write_key)
            VALUES('00000000-0000-0000-0000-000000997172',992101,'00000000-0000-0000-0000-000000992161',
                'LINE_NOTE',2,'Explain the delay.','retry.ts',20,22,'NEW','PLANNED','00000000-0000-0000-0000-000000997172');
            """);
        var planned = PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + WORK + "')");
        assertThat(planned).isNotEqualTo(approved);
        PracticeV1CutoverMigrationTest.execute(
                database, "UPDATE feedback_copy SET side='OLD' WHERE id='00000000-0000-0000-0000-000000997172'");
        var changedSide =
                PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + WORK + "')");
        assertThat(changedSide).isNotEqualTo(planned);
        PracticeV1CutoverMigrationTest.execute(
                database,
                "UPDATE feedback_copy SET body='Change the delay.' WHERE id='00000000-0000-0000-0000-000000997172'");
        assertThat(PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + WORK + "')"))
                .isNotEqualTo(changedSide);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldKeepShownChatWordsSeparateFromThePreparedBrief(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        var prepared =
                PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + CHAT + "')");
        PracticeV1CutoverMigrationTest.execute(
                database, "UPDATE feedback_copy SET body='Actual shown words.' WHERE feedback_id='" + CHAT + "'");
        assertThat(PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + CHAT + "')"))
                .isEqualTo(prepared);
        PracticeV1CutoverMigrationTest.execute(
                database, "UPDATE feedback SET body='A different prepared brief.' WHERE id='" + CHAT + "'");
        assertThat(PracticeV1CutoverMigrationTest.column(database, "SELECT feedback_content_digest('" + CHAT + "')"))
                .isNotEqualTo(prepared);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void shouldHashAllPreparedPiecesInTheirInputOrder(String context) throws Exception {
        var database = PracticeV1CutoverMigrationTest.releasedDatabase(context);
        PracticeV1CutoverMigrationTest.upgrade(database, context);
        assertThat(PracticeV1CutoverMigrationTest.column(database, """
            SELECT feedback_package_digest(digests) FROM (VALUES
                (1,ARRAY[]::text[]),
                (2,ARRAY[repeat('0',64)]),
                (3,ARRAY[repeat('0',64),repeat('1',64)]),
                (4,ARRAY[repeat('1',64),repeat('0',64)]),
                (5,ARRAY[repeat('0',64),repeat('0',64)])
            ) vectors(position,digests) ORDER BY position
            """))
                .containsExactly(
                        "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
                        "a1b93e4c095824efec3a2d5c5fe52d21f876ba16c57a13fd1b3931a7eb22a218",
                        "fb318ac9ca7ca216120530046df407732ab70804f2ea6d6425ac147daf2f7041",
                        "3b695784494a19c90f1c0ecdc394c0c1e638da01f8ca36dea8f285f01877a5dc",
                        "b44dbed3b71e44376453efcfd2d699c24e8b9428b85118f369e77a0d9e3fbf94");
        for (String value : new String[] {"ARRAY[NULL]::text[]", "ARRAY['invalid']", "ARRAY[[repeat('0',64)]]"}) {
            assertThatThrownBy(() -> PracticeV1CutoverMigrationTest.column(
                            database, "SELECT feedback_package_digest(" + value + ")"))
                    .isInstanceOfSatisfying(
                            SQLException.class,
                            exception -> assertThat(exception.getSQLState()).isEqualTo("23514"));
        }
    }
}
