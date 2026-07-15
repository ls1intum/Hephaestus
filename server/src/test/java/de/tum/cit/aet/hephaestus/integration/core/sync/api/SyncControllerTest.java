package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Unit coverage for {@link SyncController}'s local exception handlers — specifically the executor
 * saturation path: a rejected async dispatch (the job row is already finalized by
 * {@code SyncStatusService.triggerSync}) must surface to the client as 503, not a 500.
 */
class SyncControllerTest extends BaseUnitTest {

    private final SyncController controller = new SyncController(null);

    @Test
    void handleDispatchRejected_mapsTaskRejectedExceptionTo503ProblemDetail() {
        ProblemDetail problem = controller.handleDispatchRejected(new TaskRejectedException("executor saturated"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Sync dispatch rejected");
        assertThat(problem.getDetail()).contains("busy").contains("retry");
    }
}
