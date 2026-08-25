package de.tum.cit.aet.hephaestus.integration.slack.connect;

import org.jspecify.annotations.Nullable;

/**
 * Optional body for the Slack test-message probe. When {@code channelId} is present and non-blank,
 * the probe targets that channel (so an admin can validate a typed-but-unsaved channel); otherwise
 * it falls back to the persisted notification channel.
 */
public record SlackTestMessageRequestDTO(@Nullable String channelId) {}
