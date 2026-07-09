package de.tum.cit.aet.hephaestus.integration.slack.preferences;

import java.util.List;
import org.jspecify.annotations.NonNull;

public record SlackUserPreferencesDTO(@NonNull List<SlackWorkspacePreferencesDTO> workspaces) {}
