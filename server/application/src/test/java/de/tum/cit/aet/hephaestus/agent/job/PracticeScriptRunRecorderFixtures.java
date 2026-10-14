package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public final class PracticeScriptRunRecorderFixtures {

    private PracticeScriptRunRecorderFixtures() {}

    /** Call inside the transaction that would end the attempt. */
    public static void record(
            PiResultParser parser,
            ScriptRunRepository runs,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            ReviewJobAssignments assignments,
            ReviewJob job,
            byte[] report,
            Map<ModelKind, FrozenModel> slots,
            Instant finishedAt) {
        new PracticeScriptRunRecorder(parser, runs, meterRegistry, objectMapper, assignments)
                .record(job, report, slots, finishedAt);
    }
}
