package de.tum.cit.aet.hephaestus.integration.slack.connect;

import org.jspecify.annotations.Nullable;

/**
 * Body for the Slack test-message probe. The probe targets {@code channelId}; when it is missing or
 * blank the probe reports {@code no_channel_configured} (nothing persists a default channel since
 * the digest removal).
 */
public record SlackTestMessageRequestDTO(@Nullable String channelId) {}
