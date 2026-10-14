package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

public final class TerminalUsageTestFixtures {
    private TerminalUsageTestFixtures() {}

    public static void appendProxyUsage(
            ReviewJobRepository jobs, LlmUsageRecorder recorder, ReviewJob job, ObjectMapper mapper) {
        ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), mapper);
        TerminalUsage.fromProxy(jobs.findLlmUsageById(job.getId()).orElse(null))
                .appendTo(
                        recorder,
                        jobs,
                        job.getWorkspaceId(),
                        job,
                        snapshot,
                        Objects.requireNonNull(snapshot.priceSnapshot()));
    }
}
