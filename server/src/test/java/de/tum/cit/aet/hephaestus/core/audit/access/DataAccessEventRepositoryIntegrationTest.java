package de.tum.cit.aet.hephaestus.core.audit.access;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * The subject-scoping predicate behind the GDPR export's Art. 15(1)(c) section.
 *
 * <p>Wrong one way it hides disclosures from the person they were about; wrong the other it puts every
 * workspace member's named disclosures into one person's export. Both are silent, so the predicate runs
 * against a real database rather than through a mock.
 *
 * <p>Each test owns a distinct subject id: the table is append-only in production, so the suite never
 * deletes rows and methods would otherwise see each other's.
 */
class DataAccessEventRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final AtomicLong SUBJECT_IDS = new AtomicLong(1_000L);
    private static final long SOMEONE_ELSE = 200L;
    private static final long VIEWER = 900L;
    private static final long SHARED_WORKSPACE = 1L;
    private static final long OTHER_WORKSPACE = 2L;

    @Autowired
    private DataAccessEventRepository repository;

    private long subject;

    @BeforeEach
    void allocateSubject() {
        subject = SUBJECT_IDS.incrementAndGet();
    }

    private void record(long workspaceId, Long subjectUserId, DataAccessResourceType type) {
        repository.save(
            DataAccessEvents.of(workspaceId, VIEWER, subjectUserId, type, Instant.now().truncatedTo(ChronoUnit.MILLIS))
        );
    }

    @Test
    @DisplayName("a subject gets their own named disclosures and the rosters of workspaces they are in")
    void matchesOwnDisclosuresAndRostersOfTheirWorkspaces() {
        record(SHARED_WORKSPACE, subject, DataAccessResourceType.PRACTICE_REPORT);
        record(SHARED_WORKSPACE, null, DataAccessResourceType.PRACTICE_ROSTER);
        record(SHARED_WORKSPACE, SOMEONE_ELSE, DataAccessResourceType.PRACTICE_REPORT);
        record(OTHER_WORKSPACE, null, DataAccessResourceType.PRACTICE_ROSTER);

        assertThat(repository.findForSubject(Set.of(subject), Set.of(SHARED_WORKSPACE), PageRequest.of(0, 50)))
            .as("a colleague's named report and an unrelated workspace's roster are not this subject's business")
            .extracting(DataAccessEvent::getSubjectUserId, DataAccessEvent::getWorkspaceId)
            .containsExactlyInAnyOrder(Tuple.tuple(subject, SHARED_WORKSPACE), Tuple.tuple(null, SHARED_WORKSPACE));
    }

    @Test
    @DisplayName("a named disclosure follows the subject out of a workspace they have left")
    void matchesNamedDisclosuresRegardlessOfCurrentMembership() {
        record(OTHER_WORKSPACE, subject, DataAccessResourceType.PRACTICE_REPORT);

        assertThat(repository.findForSubject(Set.of(subject), Set.of(SHARED_WORKSPACE), PageRequest.of(0, 50))).hasSize(
            1
        );
    }

    @Test
    @DisplayName("an empty id set is a valid query, not a malformed one")
    void toleratesEmptyCollections() {
        record(OTHER_WORKSPACE, subject, DataAccessResourceType.PRACTICE_REPORT);

        assertThat(repository.findForSubject(Set.of(), Set.of(SHARED_WORKSPACE), PageRequest.of(0, 50))).noneMatch(
            event -> Long.valueOf(subject).equals(event.getSubjectUserId())
        );
        assertThat(repository.findForSubject(Set.of(subject), Set.of(), PageRequest.of(0, 50))).hasSize(1);
    }

    @Test
    @DisplayName("the page size is honoured")
    void honoursThePageSize() {
        record(SHARED_WORKSPACE, subject, DataAccessResourceType.PRACTICE_REPORT);
        record(SHARED_WORKSPACE, subject, DataAccessResourceType.PRACTICE_ROSTER);
        record(SHARED_WORKSPACE, subject, DataAccessResourceType.PRACTICE_REPORT);

        assertThat(repository.findForSubject(Set.of(subject), Set.of(), PageRequest.of(0, 2))).hasSize(2);
    }
}
