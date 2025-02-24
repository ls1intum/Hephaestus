package de.tum.in.www1.hephaestus.notification;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import java.util.*;

@Component
public class MailConfig {

    private final Boolean enabled;

    @Getter
    private final InternetAddress sender;

    @Getter
    private final String signature;

    @Getter
    private final TemplateEngine templateEngine;

    @Autowired
    public MailConfig(
            @Value("${hephaestus.mail.sender}") InternetAddress sender,
            @Value("${hephaestus.mail.enabled}") boolean enabled,
            @Value("${hephaestus.mail.signature}") String mailSignature,
            TemplateEngine templateEngine
    ) {
        this.enabled = enabled;
        this.sender = sender;
        this.signature = mailSignature;
        this.templateEngine = templateEngine;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record MailConfigDto(
            String signature
    ) {}

    public MailConfigDto getConfigDto() {
        return new MailConfigDto(
                Objects.requireNonNullElse(signature, "")
        );
    }
}
