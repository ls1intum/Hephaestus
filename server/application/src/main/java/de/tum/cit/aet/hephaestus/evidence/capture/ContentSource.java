package de.tum.cit.aet.hephaestus.evidence.capture;

import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Captured source data for an account's mentor conversation. */
public interface ContentSource {
    String OUTPUT_PREFIX = "inputs/context/";

    boolean supports(MentorContextRequest request);

    default boolean required() {
        return true;
    }

    default boolean ownsPath(String path) {
        return path.startsWith(OUTPUT_PREFIX);
    }

    default Optional<JsonNode> read(long workspaceId, long accountId, String path) {
        return Optional.empty();
    }

    void contribute(MentorContextRequest request, Map<String, byte[]> files);
}
