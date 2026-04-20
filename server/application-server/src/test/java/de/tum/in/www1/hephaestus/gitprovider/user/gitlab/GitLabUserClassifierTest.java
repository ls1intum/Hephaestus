package de.tum.in.www1.hephaestus.gitprovider.user.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("GitLabUserClassifier")
class GitLabUserClassifierTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "group_185885_bot_39ba88423f879e8c9ec140214eda9548",
            "group_328643_bot_de8d541a82d8e790c0ad41b12a6823ed",
            "project_7_bot_abcdef0123456789",
        }
    )
    @DisplayName("isBot recognises GitLab group/project access token logins")
    void isBot_recognisesTokenLogins(String login) {
        assertThat(GitLabUserClassifier.isBot(login)).isTrue();
        assertThat(GitLabUserClassifier.classify(login)).isEqualTo(User.Type.BOT);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "ga84xah",
            "jennifer.wagner",
            "LukasJochim",
            "00000000014B3DCE",
            "group_185885",
            "bot_project_42",
            "group_abc_bot_123",
            "group__bot_deadbeef",
            "group_1_bot_NOTHEX",
        }
    )
    @DisplayName("isBot rejects human and malformed logins")
    void isBot_rejectsNonBotLogins(String login) {
        assertThat(GitLabUserClassifier.isBot(login)).isFalse();
        assertThat(GitLabUserClassifier.classify(login)).isEqualTo(User.Type.USER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("isBot is null-safe and treats blanks as humans")
    void isBot_nullSafe(String login) {
        assertThat(GitLabUserClassifier.isBot(login)).isFalse();
        assertThat(GitLabUserClassifier.classify(login)).isEqualTo(User.Type.USER);
    }
}
