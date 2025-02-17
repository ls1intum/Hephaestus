package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MailService {

    private final JavaMailSender javaMailSender;

    private final MailConfig mailConfig;

    @Autowired
    public MailService(JavaMailSender javaMailSender, MailConfig mailConfig) {
        this.javaMailSender = javaMailSender;
        this.mailConfig = mailConfig;
    }

    public void sendBadPracticesDetectedEmail(User user, List<String> badPractices) {
        MailBuilder mailBuilder = new MailBuilder(mailConfig, "Bad Practices Detected", "bad-practices-detected");
        mailBuilder
                .addPrimaryRecipient(user)
                .send(javaMailSender);
    }
}
