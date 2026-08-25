package de.tum.cit.aet.hephaestus.integration.scm;

import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.Vendor.GITHUB;
import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.Vendor.GITLAB;
import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.assertVendorCouldReturn;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The validator's own guard. A response-shape check that quietly accepts everything is worse than none: it
 * leaves a green tick where a proof should be. These pin both directions — the two shapes behind the
 * GitLab {@code iid} defect are rejected, and the traversal features that would otherwise reject correct
 * fixtures (fragment spreads, inline fragments, subset stubs) are honoured.
 */
class GraphQlResponseStubValidatorTest extends BaseUnitTest {

    private static final String LINK_COMMITS = "LinkCommitsToMergeRequests";
    private static final String MERGE_REQUEST_NODES = "project.mergeRequests.nodes";

    @Test
    void rejectsAKeyTheDocumentNeverSelects() {
        // `approved` is a real MergeRequest field — schema-valid, but this document never asks for it, so
        // GitLab would not send it.
        Map<String, Object> node = Map.of("iid", "42", "approved", true);

        assertThatThrownBy(() -> assertVendorCouldReturn(GITLAB, LINK_COMMITS, MERGE_REQUEST_NODES, List.of(node)))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("project.mergeRequests.nodes[0].approved")
            .hasMessageContaining("is not selected by " + LINK_COMMITS);
    }

    @Test
    void rejectsAnIntegerWhereTheSchemaDeclaresAString() {
        // MergeRequest.iid is String!, so GitLab sends "42" and never 42.
        Map<String, Object> node = Map.of("iid", 42);

        assertThatThrownBy(() -> assertVendorCouldReturn(GITLAB, LINK_COMMITS, MERGE_REQUEST_NODES, List.of(node)))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("project.mergeRequests.nodes[0].iid")
            .hasMessageContaining("String!")
            .hasMessageContaining("Integer");
    }

    @Test
    void acceptsTheCorrectedStub() {
        Map<String, Object> node = Map.of(
            "iid",
            "42",
            "state",
            "merged",
            "commitsWithoutMergeCommits",
            Map.of(
                "nodes",
                List.of(
                    Map.of(
                        "sha",
                        "abc123",
                        "authorEmail",
                        "dev@example.com",
                        "author",
                        Map.of("id", "gid://gitlab/User/1", "username", "dev")
                    )
                ),
                "pageInfo",
                Map.of("hasNextPage", false)
            )
        );

        assertThatCode(() ->
            assertVendorCouldReturn(GITLAB, LINK_COMMITS, MERGE_REQUEST_NODES, List.of(node))
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsAListFieldStubbedAsAnObject() {
        Map<String, Object> node = Map.of("commitsWithoutMergeCommits", Map.of("nodes", Map.of("sha", "abc123")));

        assertThatThrownBy(() -> assertVendorCouldReturn(GITLAB, LINK_COMMITS, MERGE_REQUEST_NODES, List.of(node)))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("commitsWithoutMergeCommits.nodes")
            .hasMessageContaining("needs a List");
    }

    @Test
    void followsFragmentSpreads() {
        // `username` reaches the stub only through ...GitLabUserFields; a validator that ignored spreads
        // would reject this correct fixture.
        Map<String, Object> discussion = Map.of(
            "id",
            "gid://gitlab/Discussion/1",
            "resolvedBy",
            Map.of("username", "dev")
        );

        assertThatCode(() ->
            assertVendorCouldReturn(
                GITLAB,
                "GetMergeRequestDiscussions",
                "project.mergeRequest.discussions.nodes",
                List.of(discussion)
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void followsInlineFragments() {
        // `parent` exists only inside `... on WorkItemWidgetHierarchy`, never on the WorkItemWidget interface.
        Map<String, Object> widget = Map.of(
            "type",
            "HIERARCHY",
            "parent",
            Map.of("iid", "10", "namespace", Map.of("fullPath", "acme/widgets"))
        );
        Map<String, Object> workItem = Map.of("iid", "5", "widgets", List.of(widget));

        assertThatCode(() ->
            assertVendorCouldReturn(GITLAB, "GetProjectWorkItemHierarchy", "project.workItems.nodes", List.of(workItem))
        ).doesNotThrowAnyException();
    }

    @Test
    void toleratesNullAndAbsentValues() {
        // Discussion.id is DiscussionID! and `resolved` is unstubbed: fixtures legitimately null out non-null
        // fields to drive defensive branches, and a stub is allowed to be a subset of the selection set.
        Map<String, Object> discussion = new HashMap<>();
        discussion.put("id", null);

        assertThatCode(() ->
            assertVendorCouldReturn(
                GITLAB,
                "GetMergeRequestDiscussions",
                "project.mergeRequest.discussions.nodes",
                List.of(discussion)
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void resolvesTheGitHubVendorToo() {
        String document = "GetRepositoryIssueNumbers";
        String path = "repository.issues.nodes";

        assertThatCode(() ->
            assertVendorCouldReturn(GITHUB, document, path, List.of(Map.of("__typename", "Issue", "number", 7)))
        ).doesNotThrowAnyException();
        assertThatThrownBy(() -> assertVendorCouldReturn(GITHUB, document, path, List.of(Map.of("number", "7"))))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Int!");
    }
}
