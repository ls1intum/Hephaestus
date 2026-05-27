/**
 * Outbound Slack messaging surface — exposed to other modules (notably
 * {@code leaderboard/}) as a named interface so the leaderboard can post weekly
 * highlights without depending on the rest of the slack/* internals (webhook,
 * lifecycle, connect).
 */
@org.springframework.modulith.NamedInterface("messaging")
package de.tum.cit.aet.hephaestus.integration.slack.messaging;
