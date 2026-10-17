/**
 * Agent module — LLM-driven sandbox runtime that executes practice review and mentor turns.
 *
 * <p>App ↔ worker boundary: see ADR 0005. The queue is the {@code review_job} table itself
 * (ADR 0025): the submission chain runs on server and its QUEUED insert IS the enqueue, while
 * {@code agent.sandbox} + {@code ReviewJobExecutor} run on worker, claiming rows and spawning
 * containers.
 */
@ApplicationModule(displayName = "Agent")
@NullMarked
package de.tum.cit.aet.hephaestus.agent;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
