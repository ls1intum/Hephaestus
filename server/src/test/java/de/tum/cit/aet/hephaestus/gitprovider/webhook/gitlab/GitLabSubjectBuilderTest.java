package de.tum.cit.aet.hephaestus.gitprovider.webhook.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Producer-side subject-builder cases. Cross-side drift against the consumer is covered by
 * {@code SubjectGrammarRoundTripTest}.
 */
class GitLabSubjectBuilderTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildsFromProjectPath() {
        JsonNode payload = parse(
            "{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"group/subgroup/myproject\"}}"
        );
        assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.group~subgroup.myproject.push");
    }

    @Test
    void buildsFromTopLevelPath() {
        JsonNode payload = parse("{\"event_name\":\"merge_request\",\"path_with_namespace\":\"myorg/repo\"}");
        assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.myorg.repo.merge_request");
    }

    @Test
    void handlesGroupScopedEvents() {
        JsonNode payload = parse("{\"object_kind\":\"group_member\",\"group\":{\"full_path\":\"parent/child\"}}");
        assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.parent~child.?.group_member");
    }

    @Test
    void parsesObjectAttributesUrl() {
        JsonNode payload = parse(
            "{\"object_kind\":\"note\",\"object_attributes\":" +
                "{\"project_id\":123,\"url\":\"https://gitlab.lrz.de/ga84xah/codereviewtest/-/merge_requests/1#note_4108500\"}}"
        );
        assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.ga84xah.codereviewtest.note");
    }

    @Test
    void sanitizesDotsAsTildes() {
        JsonNode payload = parse("{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"my.org/my.repo\"}}");
        assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.my~org.my~repo.push");
    }

    @Test
    void instanceLevelFallbackForUnknownPayloads() {
        assertThat(GitLabSubjectBuilder.build(parse("{}"))).isEqualTo("gitlab.?.?.unknown");
    }

    @Test
    void prefersObjectKindOverEventName() {
        JsonNode payload = parse(
            "{\"object_kind\":\"issue\",\"event_name\":\"issue_open\",\"project\":{\"path_with_namespace\":\"org/repo\"}}"
        );
        assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.org.repo.issue");
    }

    @Nested
    class EventNameNormalization {

        @Test
        void userGroupEventsCollapseToMember() {
            JsonNode payload = parse(
                "{\"event_name\":\"user_add_to_group\",\"group\":{\"full_path\":\"parent/child\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.parent~child.?.member");
        }

        @Test
        void subgroupEventsCollapseToSubgroup() {
            JsonNode payload = parse("{\"event_name\":\"subgroup_create\",\"group\":{\"full_path\":\"parent\"}}");
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.parent.?.subgroup");
        }

        @Test
        void projectEventsCollapseToProject() {
            JsonNode payload = parse(
                "{\"event_name\":\"project_create\",\"project\":{\"path_with_namespace\":\"org/repo\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.org.repo.project");
        }

        @Test
        void workItemCollapsesToIssue() {
            JsonNode payload = parse(
                "{\"object_kind\":\"work_item\",\"project\":{\"path_with_namespace\":\"org/repo\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.org.repo.issue");
        }
    }

    @Nested
    class SpecialCharactersAreTokenSafe {

        @Test
        void preservesWildcards() {
            JsonNode payload = parse("{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"org*/repo\"}}");
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.org*.repo.push");
        }

        @Test
        void preservesUnicode() {
            JsonNode payload = parse(
                "{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"org🚀/repo📦\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.org🚀.repo📦.push");
        }

        @Test
        void preservesAccentedCharacters() {
            JsonNode payload = parse("{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"café/naïve\"}}");
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.café.naïve.push");
        }

        @Test
        void emptyNamespaceFallsBackToInstancePlaceholder() {
            JsonNode payload = parse("{\"object_kind\":\"push\",\"project\":{\"path_with_namespace\":\"\"}}");
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.?.?.push");
        }
    }

    @Nested
    class GroupFallbackTruthiness {

        @Test
        void emptyFullPathFallsThroughToPath() {
            JsonNode payload = parse(
                "{\"event_name\":\"subgroup_create\",\"group\":{\"full_path\":\"\",\"path\":\"parent\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.parent.?.subgroup");
        }

        @Test
        void emptyFullPathAndPathFallThroughToGroupPath() {
            JsonNode payload = parse(
                "{\"event_name\":\"subgroup_create\",\"group\":{\"full_path\":\"\",\"path\":\"\",\"group_path\":\"parent\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.parent.?.subgroup");
        }
    }

    @Nested
    class ObjectAttributesUrl {

        @Test
        void missingProjectIdTreatsPathAsGroupScoped() {
            JsonNode payload = parse(
                "{\"object_kind\":\"note\",\"object_attributes\":" +
                    "{\"url\":\"https://gitlab.example.com/parent/child/-/issues/1\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.parent~child.?.note");
        }

        @Test
        void urlWithoutDashSeparatorPreservesEntirePath() {
            JsonNode payload = parse(
                "{\"object_kind\":\"note\",\"object_attributes\":" +
                    "{\"project_id\":42,\"url\":\"https://gitlab.example.com/org/repo\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.org.repo.note");
        }

        @Test
        void urlMissingSchemeShortCircuitsToInstance() {
            JsonNode payload = parse(
                "{\"object_kind\":\"note\",\"object_attributes\":{\"project_id\":42,\"url\":\"no-scheme-here\"}}"
            );
            assertThat(GitLabSubjectBuilder.build(payload)).isEqualTo("gitlab.?.?.note");
        }
    }
}
