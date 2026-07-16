package de.tum.cit.aet.hephaestus.integration.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncPhase;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncProgress;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The write-throttle contract: over-calling is free, nothing buffered is lost, and the last update
 * before a quiet period still lands.
 */
class SyncJobHandleTest extends BaseUnitTest {

    private static final long JOB_ID = 42L;

    /** Captures what actually reached the "database". */
    private static final class RecordingWriter implements SyncJobHandle.ProgressWriter {

        private final List<Map<String, Object>> writes = new ArrayList<>();
        private final List<Integer> processed = new ArrayList<>();

        @Override
        public void writeProgress(
            long jobId,
            Integer itemsProcessed,
            Integer itemsTotal,
            Map<String, Object> progressDetail
        ) {
            processed.add(itemsProcessed);
            writes.add(progressDetail);
        }

        int writeCount() {
            return writes.size();
        }
    }

    private static SyncProgress progress(String step) {
        return SyncProgress.of(SyncPhase.ISSUES, step);
    }

    @Test
    void firstProgressCall_writesImmediately() {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        handle.progress(1, 10, progress("first"));

        assertThat(writer.writeCount()).isEqualTo(1);
    }

    @Test
    void burstOfCallsWithinTheInterval_writesOnceButKeepsTheLatestValue() {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        // A runner reporting every page: the whole point is that this is free.
        for (int i = 1; i <= 50; i++) {
            handle.progress(i, 50, progress("page " + i));
        }

        assertThat(writer.writeCount()).isEqualTo(1);
        // Suppressed does not mean discarded — the newest state is buffered and readable.
        assertThat(handle.currentItemsProcessed()).isEqualTo(50);
        assertThat(handle.currentProgressDetail()).containsEntry(SyncProgress.KEY_CURRENT_STEP, "page 50");
    }

    @Test
    void flushIfDue_isANoOpWhenNothingWasSuppressed() {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);
        handle.progress(1, 10, progress("first"));

        boolean flushed = handle.flushIfDue();

        assertThat(flushed).isFalse();
        assertThat(writer.writeCount()).isEqualTo(1);
    }

    @Test
    void flushIfDue_isANoOpOnAFreshHandleThatNeverReported() {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        assertThat(handle.flushIfDue()).isFalse();
        assertThat(writer.writeCount()).isZero();
    }

    /**
     * The trailing edge. This is the regression that bites once runners report per page: a suppressed
     * update followed by a long quiet period must not sit in memory until the next call.
     */
    @Test
    void suppressedUpdate_isFlushedOnceTheIntervalElapses() throws InterruptedException {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        handle.progress(1, 10, progress("written"));
        handle.progress(2, 10, progress("suppressed — the last word before a quiet period"));
        assertThat(writer.writeCount()).isEqualTo(1);

        // Too soon: the throttle still owns the budget.
        assertThat(handle.flushIfDue()).isFalse();
        assertThat(writer.writeCount()).isEqualTo(1);

        Thread.sleep(SyncJobHandle.MIN_WRITE_INTERVAL_SECONDS * 1000 + 200);

        assertThat(handle.flushIfDue()).isTrue();
        assertThat(writer.writeCount()).isEqualTo(2);
        assertThat(writer.processed.get(1)).isEqualTo(2);
        assertThat(writer.writes.get(1)).containsEntry(
            SyncProgress.KEY_CURRENT_STEP,
            "suppressed — the last word before a quiet period"
        );
    }

    @Test
    void flushIfDue_writesAtMostOncePerSuppressedUpdate() throws InterruptedException {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        handle.progress(1, 10, progress("written"));
        handle.progress(2, 10, progress("suppressed"));
        Thread.sleep(SyncJobHandle.MIN_WRITE_INTERVAL_SECONDS * 1000 + 200);

        assertThat(handle.flushIfDue()).isTrue();
        // The sweep runs every second against every active handle; a flushed handle with no new
        // progress must not keep re-writing the same row.
        assertThat(handle.flushIfDue()).isFalse();
        assertThat(writer.writeCount()).isEqualTo(2);
    }

    @Test
    void progressAfterTheIntervalElapses_writesDirectlyWithoutNeedingASweep() throws InterruptedException {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        handle.progress(1, 10, progress("first"));
        Thread.sleep(SyncJobHandle.MIN_WRITE_INTERVAL_SECONDS * 1000 + 200);
        handle.progress(2, 10, progress("second"));

        assertThat(writer.writeCount()).isEqualTo(2);
        assertThat(handle.flushIfDue()).isFalse();
    }

    @Test
    void progressDetail_isFlattenedToTheSharedWireKeys() {
        RecordingWriter writer = new RecordingWriter();
        SyncJobHandle handle = new SyncJobHandle(JOB_ID, writer);

        handle.progress(3, 7, SyncProgress.ofResource(SyncPhase.PULL_REQUESTS, "step text", "owner/repo", 3, 7));

        assertThat(writer.writes.getFirst()).containsOnly(
            Map.entry(SyncProgress.KEY_PHASE, "pullRequests"),
            Map.entry(SyncProgress.KEY_CURRENT_STEP, "step text"),
            Map.entry(SyncProgress.KEY_CURRENT_REPOSITORY, "owner/repo"),
            Map.entry(SyncProgress.KEY_UNITS_COMPLETED, 3),
            Map.entry(SyncProgress.KEY_UNITS_TOTAL, 7)
        );
    }
}
