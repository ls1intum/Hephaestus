package de.tum.in.www1.hephaestus.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.testconfig.MockSecurityContextUtils;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("FeatureFlagService")
class FeatureFlagServiceTest extends BaseUnitTest {

    @Mock
    private FeatureProperties featureProperties;

    @InjectMocks
    private FeatureFlagService featureFlagService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("isEnabled — ROLE flags")
    class RoleFlags {

        @Test
        @DisplayName("returns true when user has the matching authority")
        void returnsTrueWhenUserHasRole() {
            setSecurityContext("testuser", FeatureFlag.MENTOR_ACCESS.key());

            assertThat(featureFlagService.isEnabled(FeatureFlag.MENTOR_ACCESS)).isTrue();
        }

        @Test
        @DisplayName("returns false when user lacks the authority")
        void returnsFalseWhenUserLacksRole() {
            setSecurityContext("testuser", FeatureFlag.MENTOR_ACCESS.key());

            assertThat(featureFlagService.isEnabled(FeatureFlag.ADMIN)).isFalse();
        }

        @Test
        @DisplayName("returns false when no security context is present")
        void returnsFalseWithNoSecurityContext() {
            assertThat(featureFlagService.isEnabled(FeatureFlag.MENTOR_ACCESS)).isFalse();
        }

        @Test
        @DisplayName("returns true for admin user with admin authority")
        void returnsTrueForAdminUser() {
            setSecurityContext("admin", FeatureFlag.ADMIN.key());

            assertThat(featureFlagService.isEnabled(FeatureFlag.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("returns correct flags when user has multiple authorities")
        void returnsCorrectFlagsWithMultipleAuthorities() {
            setSecurityContext(
                "poweruser",
                FeatureFlag.ADMIN.key(),
                FeatureFlag.MENTOR_ACCESS.key(),
                FeatureFlag.RUN_PRACTICE_REVIEW.key()
            );

            assertThat(featureFlagService.isEnabled(FeatureFlag.ADMIN)).isTrue();
            assertThat(featureFlagService.isEnabled(FeatureFlag.MENTOR_ACCESS)).isTrue();
            assertThat(featureFlagService.isEnabled(FeatureFlag.RUN_PRACTICE_REVIEW)).isTrue();
            assertThat(featureFlagService.isEnabled(FeatureFlag.NOTIFICATION_ACCESS)).isFalse();
        }
    }

    @Nested
    @DisplayName("isEnabled — CONFIG flags")
    class ConfigFlags {

        @Test
        @DisplayName("returns true when config flag is enabled")
        void returnsTrueWhenConfigEnabled() {
            when(featureProperties.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION.key())).thenReturn(true);

            assertThat(featureFlagService.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION)).isTrue();
        }

        @Test
        @DisplayName("returns false when config flag is disabled")
        void returnsFalseWhenConfigDisabled() {
            when(featureProperties.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION.key())).thenReturn(false);

            assertThat(featureFlagService.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION)).isFalse();
        }

        @Test
        @DisplayName("CONFIG flags do not depend on security context")
        void configFlagsIgnoreSecurityContext() {
            when(featureProperties.isEnabled(FeatureFlag.PRACTICE_REVIEW_FOR_ALL.key())).thenReturn(true);

            // No security context — CONFIG flags should still work
            assertThat(featureFlagService.isEnabled(FeatureFlag.PRACTICE_REVIEW_FOR_ALL)).isTrue();
        }
    }

    @Nested
    @DisplayName("allEnabled — AND composition")
    class AllEnabled {

        @Test
        @DisplayName("returns true when all flags are enabled")
        void returnsTrueWhenAllEnabled() {
            setSecurityContext("admin", FeatureFlag.ADMIN.key());
            when(featureProperties.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION.key())).thenReturn(true);

            assertThat(
                featureFlagService.allEnabled(FeatureFlag.ADMIN, FeatureFlag.GITLAB_WORKSPACE_CREATION)
            ).isTrue();
        }

        @Test
        @DisplayName("returns false when one flag is disabled")
        void returnsFalseWhenOneDisabled() {
            setSecurityContext("admin", FeatureFlag.ADMIN.key());
            when(featureProperties.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION.key())).thenReturn(false);

            assertThat(
                featureFlagService.allEnabled(FeatureFlag.ADMIN, FeatureFlag.GITLAB_WORKSPACE_CREATION)
            ).isFalse();
        }

        @Test
        @DisplayName("returns true for empty varargs")
        void returnsTrueForEmpty() {
            assertThat(featureFlagService.allEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("anyEnabled — OR composition")
    class AnyEnabled {

        @Test
        @DisplayName("returns true when at least one flag is enabled")
        void returnsTrueWhenOneEnabled() {
            // ADMIN role flag is checked first and returns true (short-circuits)
            setSecurityContext("admin", FeatureFlag.ADMIN.key());

            assertThat(
                featureFlagService.anyEnabled(FeatureFlag.ADMIN, FeatureFlag.GITLAB_WORKSPACE_CREATION)
            ).isTrue();
        }

        @Test
        @DisplayName("returns false when no flags are enabled")
        void returnsFalseWhenNoneEnabled() {
            // No security context → ROLE flags return false
            // CONFIG flags also return false (Mockito defaults unstubbed boolean to false)
            assertThat(
                featureFlagService.anyEnabled(FeatureFlag.ADMIN, FeatureFlag.GITLAB_WORKSPACE_CREATION)
            ).isFalse();
        }

        @Test
        @DisplayName("returns false for empty varargs")
        void returnsFalseForEmpty() {
            assertThat(featureFlagService.anyEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("evaluateAll")
    class EvaluateAll {

        @Test
        @DisplayName("returns a map with all flags evaluated")
        void returnsAllFlags() {
            setSecurityContext("testuser", FeatureFlag.MENTOR_ACCESS.key());
            when(featureProperties.isEnabled(FeatureFlag.PRACTICE_REVIEW_FOR_ALL.key())).thenReturn(true);

            Map<FeatureFlag, Boolean> result = featureFlagService.evaluateAll();

            assertThat(result).hasSize(FeatureFlag.values().length);
            assertThat(result.get(FeatureFlag.MENTOR_ACCESS)).isTrue();
            assertThat(result.get(FeatureFlag.ADMIN)).isFalse();
            assertThat(result.get(FeatureFlag.PRACTICE_REVIEW_FOR_ALL)).isTrue();
        }
    }

    @Nested
    @DisplayName("null safety")
    class NullSafety {

        @Test
        @DisplayName("isEnabled throws NullPointerException for null flag")
        void throwsForNullFlag() {
            assertThatThrownBy(() -> featureFlagService.isEnabled(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("flag must not be null");
        }
    }

    private void setSecurityContext(String username, String... authorities) {
        SecurityContextHolder.setContext(
            MockSecurityContextUtils.createSecurityContext(username, username + "-id", authorities, "mock-token")
        );
    }
}
