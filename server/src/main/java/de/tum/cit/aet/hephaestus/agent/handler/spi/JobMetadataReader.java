package de.tum.cit.aet.hephaestus.agent.handler.spi;

import tools.jackson.databind.JsonNode;

/**
 * Strict readers for required agent-job metadata fields, shared by every job handler and content provider
 * that parses {@code job.getMetadata()}. A missing or wrong-typed field is a job-preparation failure, not a
 * silently-defaulted value — so each reader throws {@link JobPreparationException} rather than returning a
 * fallback. Previously these were copy-pasted (and drifted) across two review handlers and a content provider.
 */
public final class JobMetadataReader {

    private JobMetadataReader() {}

    /** @return the non-blank text at {@code field}; throws if absent, null, or blank. */
    public static String requireText(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        return node.asString();
    }

    /** @return the int at {@code field}; throws if absent/null (missing) or non-numeric (wrong type). */
    public static int requireInt(JsonNode metadata, String field) {
        return requireNumber(metadata, field).asInt();
    }

    /** @return the long at {@code field}; throws if absent/null (missing) or non-numeric (wrong type). */
    public static long requireLong(JsonNode metadata, String field) {
        return requireNumber(metadata, field).asLong();
    }

    private static JsonNode requireNumber(JsonNode metadata, String field) {
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            throw new JobPreparationException("Missing required metadata field: " + field);
        }
        if (!node.isNumber()) {
            throw new JobPreparationException(
                "Expected numeric metadata field: " + field + ", got: " + node.getNodeType()
            );
        }
        return node;
    }
}
