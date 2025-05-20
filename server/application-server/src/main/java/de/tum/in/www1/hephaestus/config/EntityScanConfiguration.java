package de.tum.in.www1.hephaestus.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to explicitly define the entity scan packages.
 * This ensures all entities are correctly discovered by JPA in all environments.
 */
@Configuration
@EntityScan({
    "de.tum.in.www1.hephaestus.activity.model",
    "de.tum.in.www1.hephaestus.gitprovider",
    "de.tum.in.www1.hephaestus.mentor",
    "de.tum.in.www1.hephaestus.workspace"
})
public class EntityScanConfiguration {
    // No additional configuration needed
}
