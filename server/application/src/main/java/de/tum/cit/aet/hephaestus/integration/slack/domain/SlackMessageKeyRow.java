package de.tum.cit.aet.hephaestus.integration.slack.domain;

/** A message's exact identity within one workspace; no message content is projected. */
public record SlackMessageKeyRow(String channelId, String messageTs) {}
