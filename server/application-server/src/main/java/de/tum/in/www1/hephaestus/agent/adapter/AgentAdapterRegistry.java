package de.tum.in.www1.hephaestus.agent.adapter;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of {@link AgentAdapter} implementations, indexed by {@link AgentType}.
 *
 * <p>All adapters are injected via Spring's collection autowiring. The registry validates at
 * construction time that every {@link AgentType} has exactly one adapter — startup fails fast if
 * an adapter is missing or duplicated.
 *
 * <p>Registered as a bean via {@link AgentAdapterConfiguration}.
 */
public class AgentAdapterRegistry {

    private final Map<AgentType, AgentAdapter> adapters;

    public AgentAdapterRegistry(List<AgentAdapter> adapterList) {
        Map<AgentType, AgentAdapter> map = new EnumMap<>(AgentType.class);
        for (AgentAdapter adapter : adapterList) {
            AgentAdapter existing = map.put(adapter.agentType(), adapter);
            if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate AgentAdapter for " +
                        adapter.agentType() +
                        ": " +
                        existing.getClass().getSimpleName() +
                        " and " +
                        adapter.getClass().getSimpleName()
                );
            }
        }
        for (AgentType type : AgentType.values()) {
            if (!map.containsKey(type)) {
                throw new IllegalStateException(
                    "No AgentAdapter registered for " +
                        type +
                        ". Add a bean implementing AgentAdapter with agentType()=" +
                        type
                );
            }
        }
        this.adapters = Map.copyOf(map);
    }

    /**
     * Get the adapter for the given agent type.
     *
     * @param agentType the agent type
     * @return the adapter (never null — validated at startup)
     */
    public AgentAdapter getAdapter(AgentType agentType) {
        Objects.requireNonNull(agentType, "agentType must not be null");
        return adapters.get(agentType);
    }
}
