package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Unit tests for {@link FragmentMergingDocumentSource}.
 * <p>
 * Verifies that shared GraphQL fragment definitions are appended selectively:
 * only fragments that are actually referenced (transitively via {@code ...FragmentName}
 * spreads) are included. This satisfies GraphQL spec §5.5.1.4 which requires that
 * all defined fragments must be used.
 *
 * @see FragmentMergingDocumentSource
 */
@DisplayName("FragmentMergingDocumentSource")
class FragmentMergingDocumentSourceTest extends BaseUnitTest {

    private static Resource byteResource(String content, String description) {
        return new ByteArrayResource(content.getBytes()) {
            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    @Nested
    @DisplayName("getDocument - selective fragment appending")
    class GetDocumentTests {

        @Test
        @DisplayName("should append only fragments referenced by the operation")
        void shouldAppendOnlyReferencedFragments() {
            // Arrange — two fragments, only one is referenced by GetOrganizationProjects
            String fragments = """
                fragment ActorFields on Actor { login }
                fragment UnusedFragment on SomeType { field1 }
                """;
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(byteResource(fragments, "test-fragments"))
            );

            // Act — GetOrganizationProjects uses ...ActorFields but not ...UnusedFragment
            String document = source.getDocument("GetOrganizationProjects").block();

            // Assert
            assertThat(document).isNotNull();
            assertThat(document).contains("query GetOrganizationProjects");
            assertThat(document).contains("fragment ActorFields on Actor");
            assertThat(document).doesNotContain("UnusedFragment");
        }

        @Test
        @DisplayName("should not append any fragments when none are referenced")
        void shouldNotAppendFragmentsWhenNoneReferenced() {
            // Arrange — fragments that are NOT used by GetRepository
            String fragments = """
                fragment ProjectV2BaseFields on ProjectV2 { id title }
                fragment ProjectV2OwnerFields on ProjectV2Owner { login }
                """;
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(byteResource(fragments, "test-fragments"))
            );

            // Act — GetRepository does NOT reference any of these fragments
            String document = source.getDocument("GetRepository").block();

            // Assert — the document should be the raw operation without any appended fragments
            assertThat(document).isNotNull();
            assertThat(document).contains("query GetRepository");
            assertThat(document).doesNotContain("fragment ProjectV2BaseFields");
            assertThat(document).doesNotContain("fragment ProjectV2OwnerFields");
        }

        @Test
        @DisplayName("should resolve transitive fragment dependencies")
        void shouldResolveTransitiveFragmentDependencies() {
            // Arrange — FragA references ...FragB, so loading a doc that uses ...FragA
            // should pull in both FragA and FragB
            String fragments = """
                fragment FragA on Type1 { field1 creator { ...FragB } }
                fragment FragB on Type2 { field2 }
                fragment FragC on Type3 { field3 }
                """;
            // Create a simple operation document that uses ...FragA
            Resource opDir = byteResource("query TestOp { node { ...FragA } }", "test-operations");
            // Use the fragment resource approach but we need an actual file on the classpath.
            // Instead, test the transitive resolution via the real operations directory
            // and a document that references ProjectV2ItemFields (which references ActorFieldsCompact)
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(new ClassPathResource("graphql/github/fragments/ProjectFragments.graphql"))
            );

            // Act — GetProjectItems uses ...ProjectV2ItemFields which uses ...ActorFieldsCompact
            String document = source.getDocument("GetProjectItems").block();

            // Assert — both the direct and transitive dependencies are included
            assertThat(document).isNotNull();
            assertThat(document).contains("fragment ProjectV2ItemFields on ProjectV2Item");
            assertThat(document).contains("fragment ActorFieldsCompact on Actor");
            assertThat(document).contains("fragment ProjectV2ItemContentFields on ProjectV2ItemContent");
            assertThat(document).contains("fragment RequestedReviewerFields on RequestedReviewer");
        }

        @Test
        @DisplayName("should throw when document is not found")
        void shouldThrowWhenDocumentNotFound() {
            // Arrange
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(byteResource("fragment F on T { f }", "test-fragment"))
            );

            // Act & Assert — ResourceDocumentSource throws IllegalStateException for missing documents
            assertThatThrownBy(() -> source.getDocument("NonExistentDocument").block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NonExistentDocument");
        }

        @Test
        @DisplayName("should work with actual ProjectFragments.graphql and include correct subset")
        void shouldWorkWithActualFragmentFileSelectively() {
            // Arrange
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(new ClassPathResource("graphql/github/fragments/ProjectFragments.graphql"))
            );

            // Act — GetOrganizationProjects uses ActorFields, ProjectV2BaseFields, ProjectV2OwnerFields
            String document = source.getDocument("GetOrganizationProjects").block();

            // Assert — referenced fragments are present
            assertThat(document).isNotNull();
            assertThat(document).contains("query GetOrganizationProjects");
            assertThat(document).contains("fragment ProjectV2BaseFields on ProjectV2");
            assertThat(document).contains("fragment ProjectV2OwnerFields on ProjectV2Owner");
            assertThat(document).contains("fragment ActorFields on Actor");

            // Assert — unreferenced fragments are NOT present
            assertThat(document).doesNotContain("fragment FieldValueFields on ProjectV2ItemFieldValue");
            assertThat(document).doesNotContain("fragment ProjectV2FieldConfigFields on ProjectV2FieldConfiguration");
            assertThat(document).doesNotContain("fragment ProjectV2StatusUpdateFields on ProjectV2StatusUpdate");
        }

        @Test
        @DisplayName(
            "should resolve FieldValueFieldsCostOptimized from shared fragment pool with transitive RequestedReviewerFields"
        )
        void shouldResolveFieldValueFieldsCostOptimizedFromSharedPool() {
            // Arrange — use the real ProjectFragments.graphql (which now contains FieldValueFieldsCostOptimized)
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(new ClassPathResource("graphql/github/fragments/ProjectFragments.graphql"))
            );

            // Act — GetProjectItems uses ...FieldValueFieldsCostOptimized (now resolved from shared pool)
            String document = source.getDocument("GetProjectItems").block();

            // Assert — FieldValueFieldsCostOptimized is resolved from the shared fragment pool
            assertThat(document).isNotNull();
            assertThat(document).contains("query GetProjectItems");
            assertThat(document).contains("fragment FieldValueFieldsCostOptimized on ProjectV2ItemFieldValue");

            // Assert — transitive dependency: FieldValueFieldsCostOptimized references ...RequestedReviewerFields
            assertThat(document).contains("fragment RequestedReviewerFields on RequestedReviewer");

            // Assert — FieldValueFieldsCostOptimized is NOT defined inline in GetProjectItems.graphql anymore
            // It should appear exactly once (from the shared pool), not twice
            int firstIndex = document.indexOf("fragment FieldValueFieldsCostOptimized");
            int secondIndex = document.indexOf("fragment FieldValueFieldsCostOptimized", firstIndex + 1);
            assertThat(secondIndex).as("FieldValueFieldsCostOptimized should appear exactly once").isEqualTo(-1);
        }

        @Test
        @DisplayName("should resolve FieldValueFieldsCostOptimized for embedded query files")
        void shouldResolveFieldValueFieldsCostOptimizedForEmbeddedQueries() {
            // Arrange
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(new ClassPathResource("graphql/github/fragments/ProjectFragments.graphql"))
            );

            // Act — embedded queries now use ...FieldValueFieldsCostOptimized spread
            String issueItems = source.getDocument("GetIssueProjectItems").block();
            String prItems = source.getDocument("GetPullRequestProjectItems").block();

            // Assert — both resolve the shared fragment with all 11 field value types
            for (String doc : List.of(issueItems, prItems)) {
                assertThat(doc).isNotNull();
                assertThat(doc).contains("fragment FieldValueFieldsCostOptimized on ProjectV2ItemFieldValue");
                assertThat(doc).contains("fragment RequestedReviewerFields on RequestedReviewer");
                // Verify coverage of complex field value types (beyond the original 5 inline types)
                assertThat(doc).contains("ProjectV2ItemFieldLabelValue");
                assertThat(doc).contains("ProjectV2ItemFieldMilestoneValue");
                assertThat(doc).contains("ProjectV2ItemFieldUserValue");
                assertThat(doc).contains("ProjectV2ItemFieldRepositoryValue");
                assertThat(doc).contains("ProjectV2ItemFieldPullRequestValue");
                assertThat(doc).contains("ProjectV2ItemFieldReviewerValue");
            }
        }

        @Test
        @DisplayName("should not contaminate non-project queries with any fragments")
        void shouldNotContaminateNonProjectQueries() {
            // Arrange
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of(new ClassPathResource("graphql/github/fragments/ProjectFragments.graphql"))
            );

            // Act — GetRepository, GetRepositoryLabels, GetRepositoryCollaborators don't use any fragments
            String getRepo = source.getDocument("GetRepository").block();
            String getLabels = source.getDocument("GetRepositoryLabels").block();
            String getCollaborators = source.getDocument("GetRepositoryCollaborators").block();

            // Assert — no fragment definitions should be appended
            for (String doc : List.of(getRepo, getLabels, getCollaborators)) {
                assertThat(doc).doesNotContain("fragment ActorFields");
                assertThat(doc).doesNotContain("fragment ProjectV2");
                assertThat(doc).doesNotContain("fragment FieldValueFields");
                assertThat(doc).doesNotContain("fragment RequestedReviewerFields");
            }
        }
    }

    @Nested
    @DisplayName("parseFragments - fragment parsing")
    class ParseFragmentsTests {

        @Test
        @DisplayName("should parse multiple fragment definitions from raw content")
        void shouldParseMultipleFragments() {
            // Arrange
            String raw = """
                fragment Frag1 on Type1 { field1 }
                fragment Frag2 on Type2 { field2 }
                fragment Frag3 on Type3 { field3 nested { subfield } }
                """;

            // Act
            Map<String, String> fragments = FragmentMergingDocumentSource.parseFragments(raw);

            // Assert
            assertThat(fragments).hasSize(3);
            assertThat(fragments).containsKeys("Frag1", "Frag2", "Frag3");
            assertThat(fragments.get("Frag1")).startsWith("fragment Frag1 on Type1");
            assertThat(fragments.get("Frag3")).contains("nested { subfield }");
        }

        @Test
        @DisplayName("should return empty map for blank content")
        void shouldReturnEmptyForBlankContent() {
            assertThat(FragmentMergingDocumentSource.parseFragments("")).isEmpty();
            assertThat(FragmentMergingDocumentSource.parseFragments("   ")).isEmpty();
            assertThat(FragmentMergingDocumentSource.parseFragments(null)).isEmpty();
        }

        @Test
        @DisplayName("should handle fragments with nested braces and spreads")
        void shouldHandleComplexFragments() {
            // Arrange — fragment that references another fragment
            String raw = """
                fragment Outer on Type1 {
                    field1
                    nested {
                        ...Inner
                    }
                }
                fragment Inner on Type2 {
                    field2
                }
                """;

            // Act
            Map<String, String> fragments = FragmentMergingDocumentSource.parseFragments(raw);

            // Assert
            assertThat(fragments).hasSize(2);
            assertThat(fragments.get("Outer")).contains("...Inner");
            assertThat(fragments.get("Inner")).contains("field2");
        }
    }

    @Nested
    @DisplayName("constructor - fragment loading")
    class ConstructorTests {

        @Test
        @DisplayName("should throw IllegalStateException when fragment resource does not exist")
        void shouldThrowWhenFragmentResourceMissing() {
            // Arrange
            Resource missingResource = new ClassPathResource("nonexistent/fragment.graphql");

            // Act & Assert
            assertThatThrownBy(() ->
                new FragmentMergingDocumentSource(
                    List.of(new ClassPathResource("graphql/github/operations/")),
                    List.of(".graphql"),
                    List.of(missingResource)
                )
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load GraphQL fragment resource");
        }

        @Test
        @DisplayName("should accept empty fragment list without error")
        void shouldAcceptEmptyFragmentList() {
            // Arrange & Act
            FragmentMergingDocumentSource source = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource("graphql/github/operations/")),
                List.of(".graphql"),
                List.of()
            );

            // Assert — document should load without appended fragments
            String document = source.getDocument("GetOrganizationProjects").block();
            assertThat(document).isNotNull();
            assertThat(document).contains("query GetOrganizationProjects");
            // No fragments should be appended since none were provided
            assertThat(document).doesNotContain("fragment ProjectV2BaseFields");
        }
    }
}
