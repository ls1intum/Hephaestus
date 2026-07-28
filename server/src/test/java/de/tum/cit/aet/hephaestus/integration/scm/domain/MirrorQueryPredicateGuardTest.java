package de.tum.cit.aet.hephaestus.integration.scm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.MentorContextQueryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * Guards the two ways a mirror query silently counts or returns rows it should not.
 *
 * <p><b>Why this asserts on the query text rather than on returned rows.</b> Both defects live
 * entirely in JPQL semantics, and neither tier that could execute JPQL catches them cheaply: unit
 * tests mock the repository interface, so a mock returns whatever the test stubs regardless of what
 * the {@code @Query} says, and the integration tier needs a live Postgres. The predicates are
 * therefore verified where they are actually written. A missing predicate fails here, in the tier
 * every contributor runs first, instead of surfacing as a wrong number on an admin page.
 */
class MirrorQueryPredicateGuardTest extends BaseUnitTest {

    /** Matches a JPQL {@code FROM Issue <alias>} range declaration, including inside a subquery. */
    private static final Pattern ISSUE_RANGE = Pattern.compile("\\bFROM\\s+Issue\\s+(\\w+)\\b");

    @Nested
    @DisplayName("single-table discriminator")
    class Discriminator {

        /**
         * {@code Issue} and {@code PullRequest} share one table under single-table inheritance, so a
         * JPQL query rooted at {@code Issue} without {@code TYPE(i) = Issue} also returns merge
         * requests. On a GitLab workspace that makes the mentor list a merge request as an assigned
         * open issue — and disagrees with the typed open-issue count in the same repository.
         *
         * <p>Scanned generically over every {@code @Query} in the interface (subqueries included) so a
         * newly added issue query inherits the guard rather than needing a new test.
         */
        @Test
        void everyIssueRootedMentorQueryRestrictsToTheIssueDiscriminator() {
            Map<String, String> offenders = new LinkedHashMap<>();

            for (Method method : MentorContextQueryRepository.class.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || query.nativeQuery()) {
                    continue;
                }
                String jpql = normalize(query.value());
                for (String alias : issueAliases(jpql)) {
                    String required = "TYPE(" + alias + ")=Issue";
                    if (!jpql.replace(" ", "").contains(required)) {
                        offenders.put(method.getName(), alias);
                    }
                }
            }

            assertThat(offenders)
                .as(
                    "JPQL rooted at Issue without TYPE(<alias>) = Issue also returns PullRequest rows " +
                        "(single-table inheritance) — a GitLab merge request would surface as an issue. " +
                        "Offending method -> unguarded alias"
                )
                .isEmpty();
        }

        /**
         * Pins the specific regression: the assigned-open-issues list must not be able to return a
         * merge request. Kept alongside the generic scan so the failure names the actual defect.
         */
        @Test
        void assignedOpenIssuesExcludesMergeRequests() throws NoSuchMethodException {
            String jpql = normalize(
                MentorContextQueryRepository.class.getMethod("findAssignedOpenIssues", Long.class, Long.class)
                    .getAnnotation(Query.class)
                    .value()
            );

            assertThat(jpql.replace(" ", ""))
                .as("findAssignedOpenIssues must exclude PullRequest rows, like its sibling count query")
                .contains("TYPE(i)=Issue");
        }
    }

    @Nested
    @DisplayName("tombstoned-parent exclusion")
    class TombstonedParents {

        /**
         * Sub-entity rows carry no tombstone of their own — they go away with the issue or pull
         * request they hang off, so the parent's {@code deletedAt} is the only signal there is.
         * Counting children of a tombstoned parent reintroduces on the child row exactly the
         * permanent inflation the deletion sweep removes from the parent: the admin would watch an
         * issue count fall while its comment count stayed put.
         *
         * <p>Commits are deliberately absent: they hang off the repository directly, not off a
         * tombstonable issue or pull request, so there is no parent tombstone to honour.
         */
        @Test
        void everySyncStatusSubEntityCountExcludesChildrenOfTombstonedParents() {
            record Guarded(Class<?> repository, String parentPath) {}

            List<Guarded> guarded = List.of(
                new Guarded(IssueCommentRepository.class, "c.issue"),
                new Guarded(PullRequestReviewRepository.class, "r.pullRequest"),
                new Guarded(PullRequestReviewCommentRepository.class, "c.pullRequest")
            );

            for (Guarded entry : guarded) {
                String jpql = normalize(countQueryOf(entry.repository()));

                assertThat(jpql)
                    .as(
                        "%s.countGroupedByRepositoryIds must exclude rows whose parent is tombstoned " +
                            "(%s.deletedAt IS NULL), consistent with the issue and pull-request counts",
                        entry.repository().getSimpleName(),
                        entry.parentPath()
                    )
                    .contains(entry.parentPath() + ".deletedAt IS NULL");
            }
        }

        /**
         * The exclusion must not cost a query. Each predicate rides the parent association the
         * grouping already joins, so the read model stays at one grouped statement per entity class
         * for the whole connection rather than one per repository.
         */
        @Test
        void theExclusionStaysInsideTheExistingGroupedQuery() {
            String jpql = normalize(countQueryOf(IssueCommentRepository.class));

            assertThat(jpql).contains("GROUP BY");
            assertThat(countOccurrences(jpql, "SELECT"))
                .as("exactly one SELECT: the grouped count, with no correlated subquery added by the tombstone filter")
                .isEqualTo(1);
        }
    }

    private static String countQueryOf(Class<?> repository) {
        for (Method method : repository.getDeclaredMethods()) {
            if (!"countGroupedByRepositoryIds".equals(method.getName())) {
                continue;
            }
            Query query = method.getAnnotation(Query.class);
            assertThat(query)
                .as("%s.countGroupedByRepositoryIds must carry an explicit @Query", repository.getSimpleName())
                .isNotNull();
            return query.value();
        }
        throw new AssertionError(repository.getSimpleName() + " declares no countGroupedByRepositoryIds");
    }

    private static List<String> issueAliases(String jpql) {
        List<String> aliases = new ArrayList<>();
        Matcher matcher = ISSUE_RANGE.matcher(jpql);
        while (matcher.find()) {
            aliases.add(matcher.group(1));
        }
        return aliases;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        int at;
        while ((at = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from = at + needle.length();
        }
        return count;
    }

    /** Collapses the whitespace a text block adds so predicates match regardless of line wrapping. */
    private static String normalize(String jpql) {
        return jpql.replaceAll("\\s+", " ").trim();
    }
}
