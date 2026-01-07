package de.tum.in.www1.hephaestus.practices.spi;

import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeNotificationData;
import org.springframework.lang.NonNull;

/**
 * Service Provider Interface for sending bad practice notifications.
 * <p>
 * This abstraction decouples the activity module from the notification module,
 * allowing the bad practice detector to trigger notifications without direct
 * dependency on mail services or other notification mechanisms.
 * <p>
 * Implementations should handle:
 * <ul>
 * <li>Email notifications</li>
 * <li>Slack notifications</li>
 * <li>GitHub comments (future)</li>
 * </ul>
 */
public interface BadPracticeNotificationSender {
    /**
     * Sends a notification about detected bad practices in a pull request.
     *
     * @param notificationData the notification payload containing PR and bad practice details
     */
    void sendBadPracticeNotification(@NonNull BadPracticeNotificationData notificationData);
}
