/**
 * Agent module — LLM-driven sandbox runtime that executes practice review and mentor turns.
 *
 * <p>App ↔ worker boundary: see ADR 0005. The queue is the {@code agent_job} table itself
 * (ADR 0025): the submission chain runs on server and its QUEUED insert IS the enqueue, while
 * {@code agent.sandbox} + {@code AgentJobExecutor} run on worker, claiming rows and spawning
 * containers.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Agent")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.agent;
