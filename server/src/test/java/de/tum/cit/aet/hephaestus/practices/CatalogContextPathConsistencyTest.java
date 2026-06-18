package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keystone anti-drift guard for the practice catalogue's references to materialised agent context.
 *
 * <p>The live evaluation found the criteria citing a FICTIONAL prefix {@code context/target/} after the
 * sandbox-ABI rename moved provider output to {@code inputs/context/} — so the agent was told to read paths
 * that do not exist and silently returned NOT_APPLICABLE for ships-tests, honours-linked, and the reviewer
 * practices. Criteria prose is not type-checked, so this test is the contract: every context path the
 * catalogue names MUST be a path the providers actually emit, and the dead prefix must never reappear.
 */
class CatalogContextPathConsistencyTest extends BaseUnitTest {

    /** Workspace-relative files the ContentProviders actually write under {@code inputs/context/}. */
    private static final Set<String> REAL_CONTEXT_FILES = Set.of(
        "metadata.json",
        "comments.json",
        "diff.patch",
        "diff_summary.md",
        "diff_stat.txt",
        "contributor_history.json",
        "issue_summary.md",
        // The two raw SQL-only graph projections (the agent cannot get these from the mounted worktree):
        "linked_work_items.json", // LinkedWorkItemContentProvider.OUTPUT_FILE — resolved linked-issue rows
        "review_threads.json", // ReviewThreadContentProvider — review-decision/thread rows
        "general_comments.json", // GeneralReviewCommentContentProvider — conversation-tab (non-inline) MR review notes
        "project_inventory.json" // WorkspaceInventoryContentProvider.OUTPUT_FILE — whole-project issue/PR index
        // NOTE: test_presence.json + branch_graph.json were deleted (worktree-derived Transform, not content);
        // acceptance_criteria.json was a phantom (no provider ever emitted it). None may reappear.
    );

    private static final Pattern CONTEXT_PATH = Pattern.compile("inputs/context/([a-z_]+\\.[a-z]+)");

    @Test
    @DisplayName("default-catalog.json names no fictional context/target/ paths and every inputs/context/ path is real")
    void catalogueContextPathsResolveToRealProviderOutputs() throws IOException {
        String catalogue = readCatalogue();

        assertThat(catalogue)
            .as("the dead pre-rename prefix 'context/target/' must never reappear in the catalogue")
            .doesNotContain("context/target/");

        Set<String> cited = new TreeSet<>();
        Matcher m = CONTEXT_PATH.matcher(catalogue);
        while (m.find()) {
            cited.add(m.group(1));
        }
        assertThat(cited).as("catalogue should cite at least the enrichment context files").isNotEmpty();
        assertThat(REAL_CONTEXT_FILES)
            .as(
                "every inputs/context/<file> the catalogue cites must be a file a ContentProvider emits — cited=%s",
                cited
            )
            .containsAll(cited);
    }

    private static String readCatalogue() throws IOException {
        try (
            InputStream in = CatalogContextPathConsistencyTest.class.getClassLoader().getResourceAsStream(
                "practices/default-catalog.json"
            )
        ) {
            assertThat(in).as("practices/default-catalog.json must be on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
