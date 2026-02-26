package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("unit")
class GitLabEventActionTest {

    @ParameterizedTest
    @EnumSource(GitLabEventAction.class)
    void fromString_roundTrips_allValues(GitLabEventAction action) {
        assertThat(GitLabEventAction.fromString(action.getValue())).isEqualTo(action);
    }

    @Test
    void fromString_caseInsensitive() {
        assertThat(GitLabEventAction.fromString("OPEN")).isEqualTo(GitLabEventAction.OPEN);
        assertThat(GitLabEventAction.fromString("Merge")).isEqualTo(GitLabEventAction.MERGE);
        assertThat(GitLabEventAction.fromString("APPROVED")).isEqualTo(GitLabEventAction.APPROVED);
    }

    @Test
    void fromString_returnsUnknown_forNull() {
        assertThat(GitLabEventAction.fromString(null)).isEqualTo(GitLabEventAction.UNKNOWN);
    }

    @Test
    void fromString_returnsUnknown_forBlank() {
        assertThat(GitLabEventAction.fromString("")).isEqualTo(GitLabEventAction.UNKNOWN);
        assertThat(GitLabEventAction.fromString("   ")).isEqualTo(GitLabEventAction.UNKNOWN);
    }

    @Test
    void fromString_returnsUnknown_forUnrecognized() {
        assertThat(GitLabEventAction.fromString("nonexistent")).isEqualTo(GitLabEventAction.UNKNOWN);
    }

    @Test
    void getValue_returnsLowercaseString() {
        assertThat(GitLabEventAction.OPEN.getValue()).isEqualTo("open");
        assertThat(GitLabEventAction.CLOSE.getValue()).isEqualTo("close");
        assertThat(GitLabEventAction.REOPEN.getValue()).isEqualTo("reopen");
        assertThat(GitLabEventAction.MERGE.getValue()).isEqualTo("merge");
        assertThat(GitLabEventAction.UPDATE.getValue()).isEqualTo("update");
        assertThat(GitLabEventAction.APPROVED.getValue()).isEqualTo("approved");
        assertThat(GitLabEventAction.UNAPPROVED.getValue()).isEqualTo("unapproved");
        assertThat(GitLabEventAction.UNKNOWN.getValue()).isEqualTo("unknown");
    }
}
