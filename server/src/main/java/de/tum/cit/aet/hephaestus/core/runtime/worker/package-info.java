/**
 * Worker runtime substrate: capacity reporting, graceful drain, control-channel client, health
 * indicator. Substrate beans depend on {@link WorkerControlPublisher} so the transport is a
 * one-method seam.
 */
@org.springframework.modulith.NamedInterface("worker-runtime")
package de.tum.cit.aet.hephaestus.core.runtime.worker;
