/**
 * Universal SPI — the vendor-neutral contracts that modules <em>outside</em> {@code integration}
 * (workspace, contributors, agent, …) consume across the module boundary. That external boundary
 * is enforced by {@code ExternalVendorImportAllowlistTest}.
 *
 * <p>Within {@code integration}, the {@code core} module is {@code OPEN}, so vendor adapters may
 * still reach non-interfaced {@code core} internals (e.g. {@code core.connection},
 * {@code core.framework}) directly — full CLOSED encapsulation behind named interfaces is a
 * deferred follow-up. Every interface here must be vendor-neutral; vendor implementations live
 * under {@code integration/<kind>/...}.
 */
@org.springframework.modulith.NamedInterface("spi")
package de.tum.cit.aet.hephaestus.integration.core.spi;
