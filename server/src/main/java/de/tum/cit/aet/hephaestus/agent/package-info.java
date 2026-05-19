/**
 * Agent module — LLM-driven sandbox runtime that executes practice review and mentor turns.
 *
 * <p>Subpackages:
 * <ul>
 *   <li>{@code agent.job} — agent job lifecycle (submit / NATS dispatch / execute / cancel)</li>
 *   <li>{@code agent.sandbox} — Docker sandbox runtime + interactive variant for mentor</li>
 *   <li>{@code agent.proxy} — worker-side LLM proxy (credential-scoped per-job)</li>
 *   <li>{@code agent.mentor} — Pi-runner infrastructure backing the mentor SSE flow</li>
 *   <li>{@code agent.task} — sealed Task envelope shared with sandbox containers</li>
 *   <li>{@code agent.context} — context provider SPI (practices catalog, mentor aspects)</li>
 * </ul>
 *
 * <p>App ↔ worker boundary: see ADR 0005. The {@code agent.job} submission chain runs on
 * server (publishes to NATS); {@code agent.sandbox} + {@code AgentJobExecutor} run on worker
 * (consumes NATS, spawns containers).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Agent")
package de.tum.cit.aet.hephaestus.agent;
