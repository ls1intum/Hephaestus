package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("unit")
class GitLabEventTypeTest {

    @ParameterizedTest
    @EnumSource(GitLabEventType.class)
    void fromString_roundTrips_allValues(GitLabEventType type) {
        assertThat(GitLabEventType.fromString(type.getValue())).isEqualTo(type);
    }

    @Test
    void fromString_caseInsensitive() {
        assertThat(GitLabEventType.fromString("MERGE_REQUEST")).isEqualTo(GitLabEventType.MERGE_REQUEST);
        assertThat(GitLabEventType.fromString("Merge_Request")).isEqualTo(GitLabEventType.MERGE_REQUEST);
        assertThat(GitLabEventType.fromString("Push")).isEqualTo(GitLabEventType.PUSH);
    }

    @Test
    void fromString_returnsNull_forUnknown() {
        assertThat(GitLabEventType.fromString("nonexistent")).isNull();
    }

    @Test
    void fromString_returnsNull_forNull() {
        assertThat(GitLabEventType.fromString(null)).isNull();
    }

    @Test
    void fromString_returnsNull_forBlank() {
        assertThat(GitLabEventType.fromString("")).isNull();
        assertThat(GitLabEventType.fromString("   ")).isNull();
    }

    @Test
    void getValue_returnsSnakeCaseString() {
        assertThat(GitLabEventType.MERGE_REQUEST.getValue()).isEqualTo("merge_request");
        assertThat(GitLabEventType.TAG_PUSH.getValue()).isEqualTo("tag_push");
        assertThat(GitLabEventType.ISSUE.getValue()).isEqualTo("issue");
        assertThat(GitLabEventType.NOTE.getValue()).isEqualTo("note");
        assertThat(GitLabEventType.PUSH.getValue()).isEqualTo("push");
        assertThat(GitLabEventType.PIPELINE.getValue()).isEqualTo("pipeline");
    }
}
