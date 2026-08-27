package de.tum.cit.aet.hephaestus.integration.slack.preferences;

import jakarta.validation.constraints.NotNull;

public record UpdateSlackUserPreferencesRequestDTO(
    @NotNull(message = "channelMessagesAllowed must not be null") Boolean channelMessagesAllowed
) {}
