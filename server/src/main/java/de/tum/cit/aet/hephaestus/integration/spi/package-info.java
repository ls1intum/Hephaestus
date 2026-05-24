/**
 * Universal SPI — the only contracts consumed across module boundaries.
 *
 * <p>Every interface here must be vendor-neutral. Vendor implementations live under
 * {@code integration/<kind>/...}; family-specific extensions live under
 * {@code integration/<family>-lib/spi/}.
 */
@org.springframework.modulith.NamedInterface("spi")
package de.tum.cit.aet.hephaestus.integration.spi;
