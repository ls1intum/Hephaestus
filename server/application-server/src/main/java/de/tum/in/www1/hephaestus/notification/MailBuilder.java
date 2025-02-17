package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;

import java.util.*;

public class MailBuilder {
    private static final Logger log = LoggerFactory.getLogger(MailBuilder.class);
    private final MailConfig config;

    private final List<User> primarySenders;
    private final List<User> primaryRecipients;

    @Getter
    private final String subject;

    @Getter
    private final String template;

    @Getter
    private final Map<String, Object> variables;

    @Getter
    private final Set<String> notificationNames;

    public MailBuilder(MailConfig config, String subject, String template) {
        this.config = config;

        this.primarySenders = new ArrayList<>();
        this.primaryRecipients = new ArrayList<>();

        this.subject = subject;
        this.template = template;

        this.variables = new HashMap<>();
        this.variables.put("config", config.getConfigDto());

        this.notificationNames = new HashSet<>();
    }

    public MailBuilder addNotificationName(String name) {
        notificationNames.add(name);

        return this;
    }

    public MailBuilder addPrimarySender(User user) {
        this.primarySenders.add(user);

        return this;
    }

    public MailBuilder addPrimaryRecipient(User user) {
        if (primaryRecipients.contains(user)) {
            return this;
        }

        primaryRecipients.add(user);

        return this;
    }


    public void send(JavaMailSender mailSender) {
        List<User> toRecipients = new ArrayList<>();

        for (User recipient : primaryRecipients) {
            if (!recipient.isNotificationsEnabled()) {
                continue;
            }
            toRecipients.add(recipient);
        }

        for (User recipient : toRecipients) {
            try {
                MimeMessage message = mailSender.createMimeMessage();

                message.setFrom("ThesisManagement <" + config.getSender().getAddress() + ">");
                message.setSender(config.getSender());

                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient.getEmail()));

                Context templateContext = new Context();
                templateContext.setVariables(this.variables);
                templateContext.setVariable("recipient", UserInfoDTO.fromUser(recipient));

                message.setSubject(subject);

                Multipart messageContent = new MimeMultipart();

                BodyPart messageBody = new MimeBodyPart();
                messageBody.setContent(config.getTemplateEngine().process(template, templateContext), "text/html; charset=utf-8");
                messageContent.addBodyPart(messageBody);

                message.setContent(messageContent);

                if (config.isEnabled()) {
                    mailSender.send(message);
                } else {
                    log.info("Sending Mail (postfix disabled)\n{}", messageBody.getContent());
                }
            } catch (Exception exception) {
                log.warn("Failed to send email", exception);
            }
        }
    }
}
