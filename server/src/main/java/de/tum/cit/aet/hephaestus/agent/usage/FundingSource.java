package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Who funds a unit of LLM usage, which determines the cap it counts against (#1368).
 *
 * <ul>
 *   <li>{@link #INSTANCE} — instance-owned credential; counts against the instance backstop.</li>
 *   <li>{@link #WORKSPACE} — workspace BYO credential; counts against that workspace's self-cap.</li>
 * </ul>
 */
public enum FundingSource {
    INSTANCE,
    WORKSPACE,
}
