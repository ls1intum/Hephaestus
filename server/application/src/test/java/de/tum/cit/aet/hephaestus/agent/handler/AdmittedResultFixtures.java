package de.tum.cit.aet.hephaestus.agent.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Persisted proof for downstream authorization tests; admission itself must verify real bytes. */
public final class AdmittedResultFixtures {
    private AdmittedResultFixtures() {}

    public static JsonNode evidence(String sourceKind) {
        return evidence(sourceKind, "context/evidence.txt", "evidence.txt", "verified quote");
    }

    public static JsonNode evidence(String sourceKind, String sourcePath, String path, String quote) {
        var evidence = new JsonMapper().createObjectNode();
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
                .put("artifactSha256", "a".repeat(64))
                .put("quoteSha256", CitationVerification.quoteDigest(quote));
        return evidence;
    }
}
