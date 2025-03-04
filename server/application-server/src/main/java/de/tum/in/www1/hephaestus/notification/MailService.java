package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserService;
import java.util.List;

import org.keycloak.admin.client.Keycloak;
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

    public void sendBadPracticesDetectedInPullRequestEmail(User user, PullRequest pullRequest, List<PullRequestBadPractice> badPractices) {
        logger.info("Sending bad practice detected email to user: " + user.getLogin());
        if (!user.getLogin().equals("iam-flo")) return;

        String email = "fehrenstorfer@gmail.com"; //keycloak.realm(realm).users().get(String.valueOf(user.getId())).toRepresentation().getEmail();

        MailBuilder mailBuilder = new MailBuilder(mailConfig, user, email, "Bad Practices detected in your pull request", "bad-practices-detected");
        mailBuilder
            .fillUserPlaceholders(user, "user")
            .fillPullRequestPlaceholders(pullRequest, "pullRequest")
            .fillBadPracticePlaceholders(badPractices, "badPractices")
            .send(javaMailSender);
    }
}
