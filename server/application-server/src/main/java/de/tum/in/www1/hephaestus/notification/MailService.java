package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeInfo;
import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeNotificationData;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender javaMailSender;

    private final MailConfig mailConfig;

    private final Keycloak keycloak;

    private final String realm;

    public MailService(
        JavaMailSender javaMailSender,
        MailConfig mailConfig,
        Keycloak keycloak,
        @Value("${keycloak.realm}") String realm
    ) {
        this.javaMailSender = javaMailSender;
        this.mailConfig = mailConfig;
        this.keycloak = keycloak;
        this.realm = realm;
    }

    public void sendBadPracticesDetectedInPullRequestEmail(BadPracticeNotificationData notificationData) {
        logger.info("Sending bad practice detected email to user: {}", notificationData.userLogin());
        String email;

        if (notificationData.userEmail() != null && !notificationData.userEmail().isBlank()) {
            email = notificationData.userEmail();
        } else {
            try {
                var keyCloakUser = keycloak
                    .realm(realm)
                    .users()
                    .searchByUsername(notificationData.userLogin(), true)
                    .getFirst();

                email = keyCloakUser.getEmail();
            } catch (Exception e) {
                logger.error("Failed to find user in Keycloak: {}", notificationData.userLogin(), e);
                return;
            }
        }

        String subject =
            "Hephaestus: " +
            getBadPracticeString(notificationData.badPractices()) +
            " detected in your pull request #" +
            notificationData.pullRequestNumber();

        if (notificationData.workspaceSlug() == null || notificationData.workspaceSlug().isBlank()) {
            logger.warn(
                "Skipping email send because workspace slug is missing for PR {}",
                notificationData.pullRequestNumber()
            );
            return;
        }

        MailBuilder mailBuilder = new MailBuilder(
            mailConfig,
            notificationData.userLogin(),
            email,
            subject,
            "bad-practices-detected"
        );
        mailBuilder
            .fillPlaceholder(notificationData.userLogin(), "userLogin")
            .fillPlaceholder(notificationData.pullRequestNumber(), "pullRequestNumber")
            .fillPlaceholder(notificationData.pullRequestTitle(), "pullRequestTitle")
            .fillPlaceholder(notificationData.pullRequestUrl(), "pullRequestUrl")
            .fillPlaceholder(notificationData.badPractices(), "badPractices")
            .fillPlaceholder(getBadPracticeString(notificationData.badPractices()), "badPracticeString")
            .fillPlaceholder(notificationData.repositoryName(), "repository")
            .fillPlaceholder(notificationData.workspaceSlug(), "workspaceSlug")
            .send(javaMailSender);
    }

    private String getBadPracticeString(List<BadPracticeInfo> badPractices) {
        int size = badPractices == null ? 0 : badPractices.size();
        if (size == 1) {
            return "1 bad practice";
        } else if (size > 1) {
            return size + " bad practices";
        } else {
            return "no bad practices";
        }
    }
}
