package de.tum.cit.aet.hephaestus.core.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code hephaestus.tenancy.*} configuration properties.
 *
 * <p>Default {@link TenancyEnforcement#LOG} in production-ish profiles (staging canary
 * before flipping to THROW after one calendar week of clean counter readings);
 * {@code application-test.yml} overrides to THROW so tests fail loudly on tenancy bugs.
 */
@ConfigurationProperties(prefix = "hephaestus.tenancy")
public record TenancyEnforcementProperties(TenancyEnforcement enforcement) {
    public TenancyEnforcementProperties {
        if (enforcement == null) {
            enforcement = TenancyEnforcement.LOG;
        }
    }
}
