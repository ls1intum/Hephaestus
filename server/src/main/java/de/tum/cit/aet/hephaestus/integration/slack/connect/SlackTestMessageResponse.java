package de.tum.cit.aet.hephaestus.integration.slack.connect;

import org.jspecify.annotations.Nullable;

/** Result of the Slack test-message probe; carries the Slack error code on failure. */
public record SlackTestMessageResponse(boolean ok, @Nullable String channelId, @Nullable String slackError) {}
