/**
 * Worker runtime substrate: capacity reporting, graceful drain, WSS control-channel client,
 * health indicator. Lives under {@code agent} because it coordinates the agent's runtime — agent
 * jobs flow through {@link WorkerCapacityState} and the drain coordinator owns the
 * {@code ReviewJobExecutor} lifecycle. Wire protocol records are shared with the hub via
 * {@code core.runtime.worker.protocol}.
 */
@NamedInterface("worker-runtime")
@NullMarked
package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
