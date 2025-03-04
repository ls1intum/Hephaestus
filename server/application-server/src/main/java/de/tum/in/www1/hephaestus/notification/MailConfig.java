package de.tum.in.www1.hephaestus.notification;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.*;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

@Component
public class MailConfig {

    private final Boolean enabled;

    @Getter
    private final InternetAddress sender;

    @Getter
    private final String signature;

    @Getter
    private final String clientHost;

    @Getter
    private final TemplateEngine templateEngine;

    @Autowired
    public MailConfig(
        @Value("${hephaestus.mail.sender}") InternetAddress sender,
        @Value("${hephaestus.mail.enabled}") boolean enabled,
        @Value("${hephaestus.mail.signature}") String mailSignature,
        @Value("${hephaestus.webapp.url}") String clientHost,
        TemplateEngine templateEngine
    ) {
        this.enabled = enabled;
        this.sender = sender;
        this.signature = mailSignature;
        this.clientHost = clientHost;
        this.templateEngine = templateEngine;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
