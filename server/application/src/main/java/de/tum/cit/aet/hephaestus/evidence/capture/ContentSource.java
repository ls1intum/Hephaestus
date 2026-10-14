package de.tum.cit.aet.hephaestus.evidence.capture;

import java.util.Map;

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

    void contribute(MentorContextRequest request, Map<String, byte[]> files);
}
