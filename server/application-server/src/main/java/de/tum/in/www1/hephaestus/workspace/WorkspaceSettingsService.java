package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for workspace configuration and settings management.
 *
 * <p>Handles:
 * <ul>
 *   <li>Leaderboard schedule configuration</li>
 *   <li>Notification settings (Slack integration)</li>
 *   <li>Token/credential management</li>
 *   <li>Visibility settings</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkspaceSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceSettingsService.class);
    private static final Pattern SLACK_CHANNEL_ID_PATTERN = Pattern.compile("^[CGD][A-Z0-9]{8,}$");

    private final WorkspaceRepository workspaceRepository;

    /**
     * Update the leaderboard schedule for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param day the day of week (1=Monday, 7=Sunday)
     * @param time the time in HH:mm format
     * @return the updated workspace
     */
    @Transactional
    public Workspace updateSchedule(Long workspaceId, Integer day, String time) {
        Workspace workspace = requireWorkspace(workspaceId);

        if (day != null) {
            if (day < 1 || day > 7) {
                throw new IllegalArgumentException("Day must be between 1 (Monday) and 7 (Sunday)");
            }
            workspace.setLeaderboardScheduleDay(day);
        }

        if (time != null) {
            validateTimeFormat(time);
            workspace.setLeaderboardScheduleTime(time);
        }

        logger.info("Updated schedule for workspace {}: day={}, time={}", workspaceId, day, time);
        return workspaceRepository.save(workspace);
    }

    /**
     * Update notification settings for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param enabled whether notifications are enabled
     * @param team the team to notify
     * @param channelId the Slack channel ID
     * @return the updated workspace
     */
    @Transactional
    public Workspace updateNotifications(Long workspaceId, Boolean enabled, String team, String channelId) {
        Workspace workspace = requireWorkspace(workspaceId);

        if (enabled != null) {
            workspace.setLeaderboardNotificationEnabled(enabled);
        }

        if (team != null) {
            workspace.setLeaderboardNotificationTeam(team);
        }

        if (channelId != null) {
            validateSlackChannelId(channelId);
            workspace.setLeaderboardNotificationChannelId(channelId);
        }

        logger.info("Updated notifications for workspace {}: enabled={}", workspaceId, enabled);
        return workspaceRepository.save(workspace);
    }

    /**
     * Update the personal access token for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param token the new token
     * @return the updated workspace
     */
    @Transactional
    public Workspace updateToken(Long workspaceId, String token) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.setPersonalAccessToken(token);
        logger.info("Updated PAT for workspace {}", workspaceId);
        return workspaceRepository.save(workspace);
    }

    /**
     * Update Slack credentials for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param slackToken the Slack bot token
     * @param slackSigningSecret the Slack signing secret
     * @return the updated workspace
     */
    @Transactional
    public Workspace updateSlackCredentials(Long workspaceId, String slackToken, String slackSigningSecret) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.setSlackToken(slackToken);
        workspace.setSlackSigningSecret(slackSigningSecret);
        logger.info("Updated Slack credentials for workspace {}", workspaceId);
        return workspaceRepository.save(workspace);
    }

    /**
     * Update public visibility for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param isPubliclyViewable whether the workspace is publicly viewable
     * @return the updated workspace
     */
    @Transactional
    public Workspace updatePublicVisibility(Long workspaceId, Boolean isPubliclyViewable) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.setIsPubliclyViewable(isPubliclyViewable);
        logger.info("Updated visibility for workspace {}: public={}", workspaceId, isPubliclyViewable);
        return workspaceRepository.save(workspace);
    }

    private Workspace requireWorkspace(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
    }

    private void validateTimeFormat(String time) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected HH:mm");
        }
    }

    private void validateSlackChannelId(String channelId) {
        if (!SLACK_CHANNEL_ID_PATTERN.matcher(channelId).matches()) {
            throw new IllegalArgumentException("Invalid Slack channel ID format");
        }
    }
}
