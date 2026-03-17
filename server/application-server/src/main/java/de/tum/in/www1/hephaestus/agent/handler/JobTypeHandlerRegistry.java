package de.tum.in.www1.hephaestus.agent.handler;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of {@link JobTypeHandler} implementations, indexed by {@link AgentJobType}.
 *
 * <p>All handlers are injected via Spring's collection autowiring. The registry validates at
 * construction time that every {@link AgentJobType} has exactly one handler — startup fails fast
 * if a handler is missing or duplicated.
 *
 * <p>Registered as a bean via {@link JobTypeHandlerConfiguration}. Follows the same pattern as
 * {@link de.tum.in.www1.hephaestus.agent.adapter.AgentAdapterRegistry}.
 */
public class JobTypeHandlerRegistry {

    private final Map<AgentJobType, JobTypeHandler> handlers;

    public JobTypeHandlerRegistry(List<JobTypeHandler> handlerList) {
        Map<AgentJobType, JobTypeHandler> map = new EnumMap<>(AgentJobType.class);
        for (JobTypeHandler handler : handlerList) {
            JobTypeHandler existing = map.put(handler.jobType(), handler);
            if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate JobTypeHandler for " +
                        handler.jobType() +
                        ": " +
                        existing.getClass().getSimpleName() +
                        " and " +
                        handler.getClass().getSimpleName()
                );
            }
        }
        for (AgentJobType type : AgentJobType.values()) {
            if (!map.containsKey(type)) {
                throw new IllegalStateException(
                    "No JobTypeHandler registered for " +
                        type +
                        ". Add a bean implementing JobTypeHandler with jobType()=" +
                        type
                );
            }
        }
        this.handlers = Map.copyOf(map);
    }

    /**
     * Get the handler for the given job type.
     *
     * @param jobType the job type
     * @return the handler (never null — validated at startup)
     */
    public JobTypeHandler getHandler(AgentJobType jobType) {
        Objects.requireNonNull(jobType, "jobType must not be null");
        return handlers.get(jobType);
    }
}
