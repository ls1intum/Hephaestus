/**
 * Agent module — LLM-driven sandbox runtime that executes practice review and mentor turns.
 *
 * <p>Subpackages:
 * <ul>
 *   <li>{@code agent.job} — agent job lifecycle (submit / NATS dispatch / execute / cancel)</li>
 *   <li>{@code agent.handler} — job-type dispatch + the practice detection → feedback delivery pipeline</li>
 *   <li>{@code agent.practice} — Pi runtime adapter for the practice-review agent (symmetric with {@code agent.mentor})</li>
 *   <li>{@code agent.mentor} — Pi runtime + interactive SSE chat backing the mentor flow</li>
 *   <li>{@code agent.context} — context provider SPI (practices catalog, mentor aspects)</li>
 *   <li>{@code agent.sandbox} — Docker sandbox runtime + interactive variant for mentor</li>
 *   <li>{@code agent.runtime} — shared Pi-runtime kernel (workspace ABI, plan/result, image, proxy auth)</li>
 *   <li>{@code agent.proxy} — worker-side LLM proxy (credential-scoped per-job)</li>
 *   <li>{@code agent.config} — the {@code AgentConfig} aggregate (model + credentials) and its seeder</li>
 *   <li>{@code agent.settings} — per-workspace agent/model bindings &amp; review policy (AI-settings API)</li>
 *   <li>{@code agent.pricing} — per-model token pricing for run cost accounting</li>
 *   <li>{@code agent.task} — sealed Task envelope shared with sandbox containers</li>
 * </ul>
 *
 * <p>App ↔ worker boundary: see ADR 0005. The {@code agent.job} submission chain runs on
 * server (publishes to NATS); {@code agent.sandbox} + {@code AgentJobExecutor} run on worker
 * (consumes NATS, spawns containers).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Agent")
package de.tum.cit.aet.hephaestus.agent;
