package de.tum.cit.aet.hephaestus.core.auth.spi;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.account.UserPreferences;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery.PreferencesView;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every feedback lane asks this one question, so two lanes cannot answer an absent row differently. */
@DisplayName("Practice feedback delivery consent")
class AccountPreferencesQueryTest extends BaseUnitTest {

    private static final long USER_ID = 11L;

    @Test
    void anAuthorWhoNeverOpenedTheirSettingsIsNotTreatedAsHavingDeclined() {
        assertThat(preferences(null).practiceFeedbackDeliveryEnabled(USER_ID)).isTrue();
    }

    @Test
    void anExplicitOptOutIsHonoured() {
        assertThat(preferences(false).practiceFeedbackDeliveryEnabled(USER_ID)).isFalse();
    }

    @Test
    void anExplicitOptInIsHonoured() {
        assertThat(preferences(true).practiceFeedbackDeliveryEnabled(USER_ID)).isTrue();
    }

    @Test
    void theAbsentRowResolvesToWhatACreatedRowWouldCarry() {
        assertThat(new UserPreferences().isPracticeFeedbackDeliveryEnabled()).isEqualTo(
            PreferencesView.PRACTICE_FEEDBACK_DELIVERY_ENABLED_BY_DEFAULT
        );
    }

    private static AccountPreferencesQuery preferences(@Nullable Boolean stored) {
        return new AccountPreferencesQuery() {
            @Override
            public Optional<PreferencesView> preferencesForLogin(String login) {
                return Optional.empty();
            }

            @Override
            public Optional<PreferencesView> preferencesForUserId(long userId) {
                return stored == null ? Optional.empty() : Optional.of(new PreferencesView(true, stored));
            }
        };
    }
}
