package de.tum.cit.aet.hephaestus.integration.slack.connect;

import org.springframework.lang.Nullable;

/** Result of the Slack test-message probe; carries the Slack error code on failure. */
public record SlackTestMessageResponse(boolean ok, @Nullable String channelId, @Nullable String slackError) {}
