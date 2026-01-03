package de.tum.in.www1.hephaestus.notification;

import jakarta.mail.internet.InternetAddress;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

@Getter
@Component
public class MailConfig {

    private final boolean enabled;

    private final InternetAddress sender;

    private final String clientHost;

    private final TemplateEngine templateEngine;

    public MailConfig(
        @Value("${hephaestus.mail.sender}") InternetAddress sender,
        @Value("${hephaestus.mail.enabled}") boolean enabled,
        @Value("${hephaestus.webapp.url}") String clientHost,
        TemplateEngine templateEngine
    ) {
        this.enabled = enabled;
        this.sender = sender;
        this.clientHost = clientHost;
        this.templateEngine = templateEngine;
    }
}
