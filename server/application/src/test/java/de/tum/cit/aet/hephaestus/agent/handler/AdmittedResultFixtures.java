package de.tum.cit.aet.hephaestus.agent.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// These fixtures support downstream authorization tests; admission tests must verify real captured bytes.
public final class AdmittedResultFixtures {
    private AdmittedResultFixtures() {}

    public static JsonNode evidence(String sourceKind) {
        return evidence(sourceKind, "context/evidence.txt", "evidence.txt", "verified quote");
    }

    public static JsonNode evidence(String sourceKind, String sourcePath, String path, String quote) {
        var evidence = new JsonMapper().createObjectNode();
        evidence.putArray("openQuestions");
        var citation = evidence.putArray("citations")
                .addObject()
                .put("sourceKind", sourceKind)
                .put("sourcePath", sourcePath)
                .put("path", path)
                .put("startLine", 1)
                .put("endLine", 1)
                .put("quote", quote);
        citation.putObject("verification")
                .put("status", "VERIFIED")
                .put("itemSha256", "a".repeat(64))
                .put("quoteSha256", CitationVerification.quoteDigest(quote));
        return evidence;
    }
}
