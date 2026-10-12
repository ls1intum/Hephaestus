package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunTargetRow;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class ReviewRunTargetMapper {

    private static final String PULL_REQUEST_ID = "pull_request_id";
    private static final String ISSUE_ID = "issue_id";
    private static final String SLACK_THREAD_ID = "slack_thread_id";
    private static final String DOCUMENT_ID = "docs_document_id";

    /**
     * The work a review names, as text: the metadata field {@link #workKey} names per review type. Columns are left
     * unqualified so the expression reads the nearest {@code agent_job} in scope. It is exactly the expression of the
     * {@code idx_agent_job_review_work} index (changelog {@code 1791557968341}), which PostgreSQL uses only for a
     * query that states the same expression; {@code ReviewWorkIdSqlTest} fails when the two diverge.
     */
    static final String WORK_ID_SQL = "CASE job_type"
            + " WHEN 'PULL_REQUEST_REVIEW' THEN metadata ->> '" + PULL_REQUEST_ID + "'"
            + " WHEN 'ISSUE_REVIEW' THEN metadata ->> '" + ISSUE_ID + "'"
            + " WHEN 'CONVERSATION_REVIEW' THEN metadata ->> '" + SLACK_THREAD_ID + "'"
            + " WHEN 'DOCUMENT_REVIEW' THEN metadata ->> '" + DOCUMENT_ID + "' END";

    /** The metadata field that names a pull request review's work, for the queries that read only those. */
    static final String PULL_REQUEST_ID_FIELD = PULL_REQUEST_ID;

    /** The review type that reviews one kind of work, and the metadata field that names the work. */
    record WorkKey(AgentJobType jobType, String idField) {}

    private ReviewRunTargetMapper() {}

    /** The inverse of {@link #from}: how a run names work of {@code kind}, or null for a kind no review reads. */
    static @Nullable WorkKey workKey(ArtifactKind kind) {
        if (kind.equals(ArtifactKinds.PULL_REQUEST)) {
            return new WorkKey(AgentJobType.PULL_REQUEST_REVIEW, PULL_REQUEST_ID);
        }
        if (kind.equals(ArtifactKinds.ISSUE)) {
            return new WorkKey(AgentJobType.ISSUE_REVIEW, ISSUE_ID);
        }
        if (kind.equals(ArtifactKinds.CONVERSATION_THREAD)) {
            return new WorkKey(AgentJobType.CONVERSATION_REVIEW, SLACK_THREAD_ID);
        }
        if (kind.equals(ArtifactKinds.DOCUMENT)) {
            return new WorkKey(AgentJobType.DOCUMENT_REVIEW, DOCUMENT_ID);
        }
        return null;
    }

    static Target from(AgentJob job) {
        return from(job.getJobType(), job.getIntegrationKind(), job.getMetadata());
    }

    static Target from(ReviewRunTargetRow row) {
        return from(row.getJobType(), row.getIntegrationKind(), row.getMetadata());
    }

    private static Target from(
            AgentJobType jobType, @Nullable IntegrationKind integrationKind, @Nullable JsonNode metadata) {
        return switch (jobType) {
            case PULL_REQUEST_REVIEW ->
                new Target(
                        ArtifactKinds.PULL_REQUEST,
                        longValue(metadata, PULL_REQUEST_ID),
                        integrationKind,
                        integerValue(metadata, "pr_number"),
                        requiredTextValue(metadata, "title", "Pull request"),
                        textValue(metadata, "repository_full_name", null),
                        null,
                        textValue(metadata, "pr_url", null));
            case ISSUE_REVIEW ->
                new Target(
                        ArtifactKinds.ISSUE,
                        longValue(metadata, ISSUE_ID),
                        integrationKind,
                        integerValue(metadata, "issue_number"),
                        requiredTextValue(metadata, "title", "Issue"),
                        textValue(metadata, "repository_full_name", null),
                        null,
                        textValue(metadata, "issue_url", null));
            case CONVERSATION_REVIEW ->
                new Target(
                        ArtifactKinds.CONVERSATION_THREAD,
                        longValue(metadata, SLACK_THREAD_ID),
                        integrationKind,
                        null,
                        "Conversation",
                        null,
                        textValue(metadata, "slack_channel_name", null),
                        null);
            // A document has no number; it sits in its collection the way a pull request sits in its
            // repository. No URL: the mirror stores a slug, and the server it hangs off is connection state
            // this mapper cannot reach — a link built from half of it would be a broken one.
            case DOCUMENT_REVIEW ->
                new Target(
                        ArtifactKinds.DOCUMENT,
                        longValue(metadata, DOCUMENT_ID),
                        integrationKind,
                        null,
                        requiredTextValue(metadata, "title", "Document"),
                        textValue(metadata, "docs_collection_name", null),
                        null,
                        null);
        };
    }

    private static @Nullable Long longValue(@Nullable JsonNode metadata, String field) {
        if (metadata == null || !metadata.path(field).isIntegralNumber()) return null;
        return metadata.path(field).asLong();
    }

    private static @Nullable Integer integerValue(@Nullable JsonNode metadata, String field) {
        if (metadata == null || !metadata.path(field).isIntegralNumber()) return null;
        return metadata.path(field).asInt();
    }

    private static @Nullable String textValue(@Nullable JsonNode metadata, String field, @Nullable String fallback) {
        if (metadata == null || !metadata.path(field).isString()) return fallback;
        String value = metadata.path(field).asString();
        return value.isBlank() ? fallback : value;
    }

    private static String requiredTextValue(@Nullable JsonNode metadata, String field, String fallback) {
        return Objects.requireNonNull(textValue(metadata, field, fallback));
    }
}
