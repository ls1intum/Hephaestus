package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal lifecycle listener required by the integration framework bootstrap for every registered
 * kind. Outline's teardown is handled elsewhere (workspace purge erases mirrored documents), so all
 * methods fall through to the no-op defaults on {@link IntegrationLifecycleListener}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineLifecycleListener implements IntegrationLifecycleListener {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }
}
