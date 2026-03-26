package de.tum.in.www1.hephaestus.agent.handler.spi;

/**
 * Marker interface for type-safe handler dispatch.
 *
 * <p>Each {@link JobTypeHandler} accepts a specific implementation of this interface in
 * {@link JobTypeHandler#createSubmission}. Handlers validate the concrete type at runtime
 * via {@code instanceof} and throw {@link IllegalArgumentException} on mismatch.
 *
 * <p>Not sealed because sealing would require this SPI type to reference implementation
 * classes via {@code permits}, creating a compile-time dependency from the SPI package
 * to handler implementations — violating the SPI isolation enforced by ArchUnit tests.
 */
public interface JobSubmissionRequest {}
