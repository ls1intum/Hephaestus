package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Records a runner's execution report as the worker does at the end of an attempt. The recorder runs in the worker
 * role, which the integration context leaves off, so a test outside this package records through here.
 */
public final class PracticeScriptRunRecorderFixtures {

    private PracticeScriptRunRecorderFixtures() {}

    /** Call inside the transaction that would end the attempt. */
    public static void record(
            PiResultParser parser,
            AgentJobPracticeScriptRunRepository runs,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            AgentJob job,
            byte[] report,
            Map<ModelKind, FrozenModel> slots,
            Instant finishedAt) {
        new PracticeScriptRunRecorder(parser, runs, meterRegistry, objectMapper).record(job, report, slots, finishedAt);
    }
}
