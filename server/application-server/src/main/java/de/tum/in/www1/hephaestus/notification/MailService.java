package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender javaMailSender;

    private final MailConfig mailConfig;

    @Autowired
    public MailService(JavaMailSender javaMailSender, MailConfig mailConfig) {
        this.javaMailSender = javaMailSender;
        this.mailConfig = mailConfig;
    }

    public void sendBadPracticesDetectedEmail(User user, String badPractice) {
        logger.info("Sending bad practice detected email to user: " + user.getLogin());
        if (!user.getLogin().equals("iam-flo"))
            return;

        MailBuilder mailBuilder = new MailBuilder(mailConfig, "Bad Practices Detected", "bad-practices-detected");
        mailBuilder
                .addPrimaryRecipient(user)
                .fillUserPlaceholders(user, "user")
                .fillBadPracticePlaceholders(badPractice, "badPractice")
                .send(javaMailSender);
    }
}
