package de.tum.in.www1.hephaestus.notification;

import de.tum.in.www1.hephaestus.config.ApplicationProperties;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

@Getter
@Component
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    private final boolean enabled;

    private final InternetAddress sender;

    private final String clientHost;

    private final TemplateEngine templateEngine;

    public MailConfig(
        MailProperties mailProperties,
        ApplicationProperties applicationProperties,
        TemplateEngine templateEngine
    ) {
        this.enabled = mailProperties.enabled();
        this.clientHost = applicationProperties.webapp().url();
        this.templateEngine = templateEngine;

        InternetAddress senderAddress = null;
        try {
            senderAddress = mailProperties.senderAddress();
        } catch (AddressException e) {
            if (this.enabled) {
                throw new IllegalStateException(
                    "Mail is enabled but sender address is invalid: " + mailProperties.sender(),
                    e
                );
            }
            log.warn("Invalid mail sender address (mail disabled): sender={}", mailProperties.sender());
        }
        this.sender = senderAddress;
    }
}
