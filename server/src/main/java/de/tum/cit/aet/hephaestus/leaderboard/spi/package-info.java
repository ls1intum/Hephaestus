/**
 * Leaderboard SPI — vendor-neutral events that integration modules subscribe to.
 *
 * <p>Today the only surface is {@link LeaderboardDigestReadyEvent}, published per
 * workspace-channel target by the weekly digest task. The Slack adapter under
 * {@code integration/slack/leaderboard/} consumes it; future Teams/Discord/email
 * adapters subscribe the same way without changes here.
 *
 * <p>Events MUST carry only primitive/value/leaderboard-side types — no vendor SDK
 * classes — so subscribers can be implemented per-kind without circular imports.
 */
@org.springframework.modulith.NamedInterface("spi")
package de.tum.cit.aet.hephaestus.leaderboard.spi;
