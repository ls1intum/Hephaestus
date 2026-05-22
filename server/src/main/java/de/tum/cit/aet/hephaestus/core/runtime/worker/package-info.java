/**
 * Worker-runtime substrate: capacity reporting, graceful drain, control-channel client, and the
 * health indicator surfaced at {@code /actuator/health}. Gated by
 * {@link de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole#WORKER_PROPERTY} on
 * {@link de.tum.cit.aet.hephaestus.core.runtime.worker.WorkerConfiguration}.
 *
 * <p>Substrate beans depend on {@link de.tum.cit.aet.hephaestus.core.runtime.worker.WorkerControlPublisher}
 * (the test seam) rather than directly on the WSS client implementation — the publisher is the
 * only contract the substrate enforces against the transport.
 *
 * <p>See ADR 0009 (WSS-over-443 wire choice for BYO MITM-proxy traversal) and the bundled
 * #1098 / #1099 epic PR.
 */
@org.springframework.modulith.NamedInterface("worker-runtime")
package de.tum.cit.aet.hephaestus.core.runtime.worker;
