package de.tum.cit.aet.hephaestus.integration.scm.github.graphql;

/**
 * Classpath locations of the shared GitHub GraphQL fragment documents, named once so the three places
 * that must agree — the client's {@code FragmentMergingDocumentSource}, the service that appends a
 * fragment to a query it assembles itself, and the two test-side validators — cannot drift apart. A
 * fragment missing from one of those lists fails only at request time, against the vendor.
 */
public final class GitHubGraphQlFragments {

    /** Fragments for projects, actors and the shapes reused across the project/issue documents. */
    public static final String PROJECT_FRAGMENTS_RESOURCE = "graphql/github/fragments/ProjectFragments.graphql";

    /** Per-commit selection set for commit metadata enrichment. */
    public static final String COMMIT_ENRICHMENT_FIELDS_RESOURCE =
        "graphql/github/fragments/CommitEnrichmentFields.graphql";

    /** Fragment name spread by {@code GetCommitMetadata.graphql} and by the batched runtime query. */
    public static final String COMMIT_ENRICHMENT_FIELDS_NAME = "CommitEnrichmentFields";

    private GitHubGraphQlFragments() {}
}
