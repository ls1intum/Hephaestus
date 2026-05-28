/**
 * Activity-write SPI — the only cross-module surface for appending {@code activity_event}
 * rows. Today the GitHub Projects v2 listener under
 * {@code integration/scm/github/project/activity/} consumes this; the {@code activity}
 * module owns the sole implementation ({@code ActivityEventService}).
 *
 * <p>Kept deliberately narrow: two methods, mirroring the in-module write API verbatim
 * (same enums, same idempotency). New cross-module callers MUST go through this SPI rather
 * than reaching for {@code ActivityEventService} directly.
 */
@org.springframework.modulith.NamedInterface("spi")
package de.tum.cit.aet.hephaestus.activity.spi;
