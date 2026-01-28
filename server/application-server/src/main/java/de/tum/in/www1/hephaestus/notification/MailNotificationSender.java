package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.practices.spi.BadPracticeNotificationSender;
import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeNotificationData;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Email implementation of {@link BadPracticeNotificationSender}.
 * <p>
 * Delegates to the existing {@link MailService} for sending bad practice
 * notifications via email.
 */
@Component
public class MailNotificationSender implements BadPracticeNotificationSender {

    private final MailService mailService;

    public MailNotificationSender(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public void sendBadPracticeNotification(@NonNull BadPracticeNotificationData notificationData) {
        mailService.sendBadPracticesDetectedInPullRequestEmail(notificationData);
    }
}
