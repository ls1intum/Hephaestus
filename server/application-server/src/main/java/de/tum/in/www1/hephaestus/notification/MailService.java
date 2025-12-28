package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticesDetectedEvent;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender javaMailSender;

    private final MailConfig mailConfig;

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    @Autowired
    public MailService(JavaMailSender javaMailSender, MailConfig mailConfig, Keycloak keycloak) {
        this.javaMailSender = javaMailSender;
        this.mailConfig = mailConfig;
        this.keycloak = keycloak;
    }

    /**
     * Sends an email notification when bad practices are detected in a pull request.
     *
     * @param event The event containing user, pull request, and bad practice data
     */
    public void sendBadPracticesDetectedEmail(BadPracticesDetectedEvent event) {
        var user = event.user();
        var pullRequest = event.pullRequest();
        var badPractices = event.badPractices();
        String workspaceSlug = event.workspaceSlug();

        logger.info("Sending bad practice detected email to user: {}", user.login());
        String email;

        try {
            UserRepresentation keyCloakUser = keycloak
                .realm(realm)
                .users()
                .searchByUsername(user.login(), true)
                .getFirst();

            email = keyCloakUser.getEmail();
        } catch (Exception e) {
            logger.error("Failed to find user in Keycloak: {}", user.login(), e);
            return;
        }

        String subject =
            "Hephaestus: " +
            getBadPracticeString(badPractices) +
            " detected in your pull request #" +
            pullRequest.number();

        if (workspaceSlug == null || workspaceSlug.isBlank()) {
            logger.warn("Skipping email send because workspace slug is missing for PR {}", pullRequest.number());
            return;
        }

        MailBuilder mailBuilder = new MailBuilder(mailConfig, user, email, subject, "bad-practices-detected");
        mailBuilder
            .fillPlaceholder(user, "user")
            .fillPlaceholder(pullRequest, "pullRequest")
            .fillPlaceholder(badPractices, "badPractices")
            .fillPlaceholder(getBadPracticeString(badPractices), "badPracticeString")
            .fillPlaceholder(pullRequest.repositoryName(), "repository")
            .fillPlaceholder(workspaceSlug, "workspaceSlug")
            .send(javaMailSender);
    }

    private String getBadPracticeString(List<BadPracticesDetectedEvent.BadPracticeData> badPractices) {
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
