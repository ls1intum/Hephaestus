package de.tum.in.www1.hephaestus.feature;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Unified service for evaluating feature flags.
 * <p>
 * Handles both {@link FeatureFlag.Kind#ROLE} flags (checked against the current
 * user's JWT authorities) and {@link FeatureFlag.Kind#CONFIG} flags (checked
 * against {@link FeatureProperties}).
 * <p>
 * This service is registered as a Spring bean and can be referenced in
 * {@code @PreAuthorize} SpEL expressions:
 * <pre>{@code
 * @PreAuthorize("@featureFlagService.isEnabled(T(de.tum.in.www1.hephaestus.feature.FeatureFlag).ADMIN)")
 * }</pre>
 *
 * @see FeatureFlag
 * @see FeatureProperties
 */
@Service
public class FeatureFlagService {

    private final FeatureProperties featureProperties;

    public FeatureFlagService(FeatureProperties featureProperties) {
        this.featureProperties = featureProperties;
    }

    /**
     * Check if a feature flag is enabled for the current authenticated user.
     * <p>
     * For {@link FeatureFlag.Kind#ROLE} flags: checks the user's JWT authorities.
     * For {@link FeatureFlag.Kind#CONFIG} flags: checks the Spring Boot property.
     *
     * @param flag the feature flag to check
     * @return true if enabled
     */
    public boolean isEnabled(FeatureFlag flag) {
        Objects.requireNonNull(flag, "flag must not be null");
        return switch (flag.kind()) {
            case ROLE -> hasAuthority(flag.key());
            case CONFIG -> featureProperties.isEnabled(flag.key());
        };
    }

    /**
     * Check if ALL of the given flags are enabled (AND composition).
     *
     * @param flags the flags to check
     * @return true only if every flag is enabled
     */
    public boolean allEnabled(FeatureFlag... flags) {
        for (FeatureFlag flag : flags) {
            if (!isEnabled(flag)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if ANY of the given flags are enabled (OR composition).
     *
     * @param flags the flags to check
     * @return true if at least one flag is enabled
     */
    public boolean anyEnabled(FeatureFlag... flags) {
        for (FeatureFlag flag : flags) {
            if (isEnabled(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluate all feature flags for the current user.
     *
     * @return a map of every {@link FeatureFlag} to its enabled state
     */
    public Map<FeatureFlag, Boolean> evaluateAll() {
        Map<FeatureFlag, Boolean> result = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            result.put(flag, isEnabled(flag));
        }
        return result;
    }

    private boolean hasAuthority(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth
            .getAuthorities()
            .stream()
            .anyMatch(a -> role.equals(a.getAuthority()));
    }
}
