package de.tum.cit.aet.hephaestus.core.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code hephaestus.tenancy.*} configuration properties.
 *
 * <p>Defaults to {@link TenancyEnforcement#THROW}. The {@code LOG} and {@code OFF} modes are
 * explicit diagnostic overrides that weaken isolation enforcement.
 */
@ConfigurationProperties(prefix = "hephaestus.tenancy")
public record TenancyEnforcementProperties(TenancyEnforcement enforcement) {
    public TenancyEnforcementProperties {
        if (enforcement == null) {
            enforcement = TenancyEnforcement.THROW;
        }
    }
}
