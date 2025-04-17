package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;

public class MailBuilder {

    private static final Logger log = LoggerFactory.getLogger(MailBuilder.class);
    private final MailConfig config;

    private final User primaryRecipient;

    private final String email;

    @Getter
    private final String subject;

    @Getter
    private final String template;

    @Getter
    private final Map<String, Object> variables;

    public MailBuilder(MailConfig config, User primaryRecipient, String email, String subject, String template) {
        this.config = config;

        this.primaryRecipient = primaryRecipient;
        this.email = email;

        this.subject = subject;
        this.template = template;

        this.variables = new HashMap<>();
        this.variables.put("config", config);
    }

    public MailBuilder fillPlaceholder(Object value, String placeholder) {
        this.variables.put(placeholder, value);
        return this;
    }

    public void send(JavaMailSender mailSender) {
        if (primaryRecipient == null || email == null) {
            log.warn("No primary recipient specified");
            return;
        }

        if (!primaryRecipient.isNotificationsEnabled()) {
            log.warn("Primary recipient has notifications disabled");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();

            message.setFrom("Hephaestus <" + config.getSender().getAddress() + ">");
            message.setSender(config.getSender());

            message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));

            Context templateContext = new Context();
            templateContext.setVariables(this.variables);
            templateContext.setVariable("recipient", UserInfoDTO.fromUser(primaryRecipient));

            message.setSubject(subject);

            Multipart messageContent = new MimeMultipart();

            BodyPart messageBody = new MimeBodyPart();
            messageBody.setContent(
                config.getTemplateEngine().process(template, templateContext),
                "text/html; charset=utf-8"
            );
            messageContent.addBodyPart(messageBody);

            message.setContent(messageContent);

            if (config.isEnabled()) {
                log.info("Sending Mail\n{}", messageBody.getContent());
                mailSender.send(message);
            } else {
                log.info("Sending Mail (postfix disabled)\n{}", messageBody.getContent());
            }
        } catch (Exception exception) {
            log.warn("Failed to send email", exception);
        }
    }
}
