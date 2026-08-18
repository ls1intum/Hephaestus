package de.tum.cit.aet.hephaestus.core.security;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutlineOriginPolicy {

    private final Set<String> allowedOrigins;

    public OutlineOriginPolicy(
        @Value("${hephaestus.integration.outline.allowed-origins:}") Set<String> allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins
            .stream()
            .map(OutlineOriginPolicy::canonicalOrigin)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String serverUrl) {
        try {
            return allowedOrigins.contains(canonicalOrigin(serverUrl));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String canonicalOrigin(String serverUrl) {
        ServerUrlValidator.validate(serverUrl);
        URI uri = URI.create(serverUrl.trim());
        String port = uri.getPort() < 0 || uri.getPort() == 443 ? "" : ":" + uri.getPort();
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + port;
    }
}
