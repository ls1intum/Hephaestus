package de.tum.in.www1.hephaestus.notification;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;

public class MailBuilder {

    private static final Logger log = LoggerFactory.getLogger(MailBuilder.class);
    private final MailConfig config;

    private final String recipientLogin;

    private final String email;

    private final String subject;

    private final String template;

    private final Map<String, Object> variables;

    /**
     * Constructor that accepts a login string instead of a User entity.
     * Used to break circular dependencies between modules.
     */
    public MailBuilder(MailConfig config, String recipientLogin, String email, String subject, String template) {
        this.config = config;
        this.recipientLogin = recipientLogin;
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
        if (recipientLogin == null || email == null) {
            log.warn("Skipped sending email: reason=noRecipient, template={}", template);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();

            message.setFrom("Hephaestus <" + config.getSender().getAddress() + ">");
            message.setSender(config.getSender());

            message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));

            Context templateContext = new Context();
            templateContext.setVariables(this.variables);
            templateContext.setVariable("recipientLogin", recipientLogin);

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
                mailSender.send(message);
            } else {
                log.info(
                    "Skipped sending email: reason=mailDisabled, userLogin={}, template={}",
                    recipientLogin,
                    template
                );
            }
        } catch (Exception exception) {
            log.warn("Failed to send email: userLogin={}, template={}", recipientLogin, template, exception);
        }
    }
}
