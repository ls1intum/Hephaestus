package de.tum.cit.aet.hephaestus.integration.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Explicit allow-list path → {@link IntegrationKind} lookup.
 *
 * <p>Never calls {@link IntegrationKind#valueOf(String)} on user-controlled input —
 * that would reflect on attacker input. The hardcoded map is the only routing source.
 */
@Component
public class IntegrationKindRouting {

    private static final Map<String, IntegrationKind> ROUTES = Map.of(
        "github", IntegrationKind.GITHUB,
        "gitlab", IntegrationKind.GITLAB,
        "slack", IntegrationKind.SLACK,
        "outline", IntegrationKind.OUTLINE
    );

    public Optional<IntegrationKind> resolve(String pathSegment) {
        if (pathSegment == null) return Optional.empty();
        return Optional.ofNullable(ROUTES.get(pathSegment.toLowerCase(Locale.ROOT)));
    }
}
