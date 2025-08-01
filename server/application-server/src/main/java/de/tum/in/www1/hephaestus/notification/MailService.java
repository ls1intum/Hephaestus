package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
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

    public void sendBadPracticesDetectedInPullRequestEmail(
        User user,
        PullRequest pullRequest,
        List<PullRequestBadPractice> badPractices
    ) {
        logger.info("Sending bad practice detected email to user: {}", user.getLogin());
        String email;

        try {
            UserRepresentation keyCloakUser = keycloak
                .realm(realm)
                .users()
                .searchByUsername(user.getLogin(), true)
                .getFirst();

            email = keyCloakUser.getEmail();
        } catch (Exception e) {
            logger.error("Failed to find user in Keycloak: {}", user.getLogin(), e);
            return;
        }

        String subject =
            "Hephaestus: " +
                    getGoodOrBadPracticeString(badPractices, "bad") +
            " detected in your pull request #" +
            pullRequest.getNumber();

        MailBuilder mailBuilder = new MailBuilder(mailConfig, user, email, subject, "bad-practices-detected");
        mailBuilder
            .fillPlaceholder(user, "user")
            .fillPlaceholder(pullRequest, "pullRequest")
            .fillPlaceholder(badPractices, "badPractices")
            .fillPlaceholder(getGoodOrBadPracticeString(badPractices,"bad"), "badPracticeString")
            .fillPlaceholder(pullRequest.getRepository().getName(), "repository")
            .send(javaMailSender);
    }

    public void sendGoodPracticesDetectedInPullRequestEmail(
        User user,
        PullRequest pullRequest,
        List<PullRequestBadPractice> badPractices
    ) {
        logger.info("Sending good practice detected email to user: {}", user.getLogin());
        String email;

        try {
            UserRepresentation keyCloakUser = keycloak
                .realm(realm)
                .users()
                .searchByUsername(user.getLogin(), true)
                .getFirst();

            email = keyCloakUser.getEmail();
        } catch (Exception e) {
            logger.error("Failed to find user in Keycloak: {}", user.getLogin(), e);
            return;
        }

        String subject =
            "Hephaestus: " +
                    getGoodOrBadPracticeString(badPractices,"good") +
            " detected in your pull request #" +
            pullRequest.getNumber();

        MailBuilder mailBuilder = new MailBuilder(mailConfig, user, email, subject, "good-practices-detected");
        mailBuilder
            .fillPlaceholder(user, "user")
            .fillPlaceholder(pullRequest, "pullRequest")
            .fillPlaceholder(badPractices, "goodPractices")
            .fillPlaceholder(getGoodOrBadPracticeString(badPractices,"good"), "goodPracticeString")
            .fillPlaceholder(pullRequest.getRepository().getName(), "repository")
            .send(javaMailSender);
    }

    private String getGoodOrBadPracticeString(List<PullRequestBadPractice> badPractices, String goodOrBad) {
        if (badPractices.size() == 1) {
            return "1 " + goodOrBad + " practice";
        } else if (badPractices.size() > 1) {
            return badPractices.size() + " " + goodOrBad + " practices";
        } else {
            return "no " + goodOrBad + " practices";
        }
    }
}
