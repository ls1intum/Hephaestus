package de.tum.in.www1.hephaestus.notification;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for email notifications.
 *
 * <p>Binds to the {@code hephaestus.mail} prefix in application configuration.
 * Controls email sender configuration and whether mail notifications are enabled.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   mail:
 *     enabled: true
 *     sender: noreply@example.com
 * }</pre>
 *
 * @param enabled whether email notifications are enabled
 * @param sender  the email address used as the sender for notifications
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mail")
public record MailProperties(
    @DefaultValue("false") boolean enabled,
    @NotBlank(message = "Mail sender address must not be blank")
    @Email(message = "Mail sender must be a valid email address")
    String sender
) {
    /**
     * Returns the sender as an InternetAddress.
     *
     * @return the sender as InternetAddress
     * @throws AddressException if the sender is not a valid email address
     */
    public InternetAddress senderAddress() throws AddressException {
        return new InternetAddress(sender);
    }
}
