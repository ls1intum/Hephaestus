/**
 * Worker control-channel frame protocol — sealed {@link de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame}
 * hierarchy with Jackson polymorphic codec. Pure data records, no transport coupling: the WSS
 * transport (worker-side client + server-side hub endpoint) depends on this package, not the
 * other way around. See ADR 0009 (WSS-over-443 wire choice for BYO MITM-proxy traversal).
 *
 * <p>Schema is frozen at v=1 once this package merges. Any subsequent change is a v=2 with
 * negotiation via {@link de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerHello#supportedVersions()}.
 */
@org.springframework.modulith.NamedInterface("worker-protocol")
package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;
