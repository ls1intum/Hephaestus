/**
 * Worker control-channel frame protocol. Sealed {@link WorkerControlFrame} hierarchy with a
 * polymorphic Jackson codec; pure data records, no transport coupling. Schema is v=1; future
 * incompatible changes negotiate via {@link WorkerHello#supportedVersions()}.
 */
@org.springframework.modulith.NamedInterface("worker-protocol")
package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;
