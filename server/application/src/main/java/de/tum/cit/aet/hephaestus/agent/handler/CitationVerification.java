package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Server-produced verdicts travel with admitted results, never with unverified model output. */
public final class CitationVerification {

    /** A pinned Git object id, in a SHA-1 or a SHA-256 repository. */
    public static final String GIT_OBJECT_ID = "(?:[0-9a-f]{40}|[0-9a-f]{64})";

    /** A lowercase hex SHA-256 digest. */
    public static final String SHA256_HEX = "[0-9a-f]{64}";

    private static final JsonMapper MAPPER = new JsonMapper();

    private CitationVerification() {}

    static void record(ObjectNode citation, AgentJob job, String artifactDigest, String quoteDigest) {
        var verdict = citation.putObject("verification");
        verdict.put("status", "VERIFIED");
        verdict.put("jobId", job.getId().toString());
        verdict.put("attempt", job.getRetryCount());
        verdict.put("artifactSha256", artifactDigest);
        verdict.put("quoteSha256", quoteDigest);
        verdict.put("scope", "EXACT_LOCATION");
        verdict.put("citationSha256", citationDigest(citation));
    }

    /**
     * Checks against the attempt that admitted the citations, not the attempt running now: a writer that continues
     * an admitted review composes from citations it did not verify, and neither re-verifies nor relabels them.
     */
    static void requireVerified(AgentJob job, @Nullable JsonNode evidence) {
        if (evidence == null) throw new JobDeliveryException("No admitted evidence");
        JsonNode citations = evidence.path("citations");
        if (!citations.isArray() || citations.isEmpty()) throw new JobDeliveryException("No admitted citations");
        requireVerifiedCitations(job.getId(), ResultAdmissionService.citationAttempt(job), citations);
    }

    /**
     * Requires every citation of a result to carry the verdict of the attempt that admitted it: the result's own, each
     * problem's and the example's. A result must rest on at least one citation; a NOT_MET result may rest on its
     * problems' alone.
     */
    static void requireVerifiedResult(AgentJob job, PracticeResult result, List<Observation> problems) {
        ArrayNode citations = MAPPER.createArrayNode();
        JsonNode evidence = result.getEvidence();
        if (evidence != null) evidence.path("citations").forEach(citations::add);
        problems.forEach(problem -> problem.getCitations().forEach(citations::add));
        JsonNode example = result.getExample();
        if (example != null && example.path("citation").isObject()) citations.add(example.path("citation"));
        if (citations.isEmpty()) throw new JobDeliveryException("No admitted citations");
        requireVerifiedCitations(job.getId(), ResultAdmissionService.citationAttempt(job), citations);
    }

    public static void requireVerifiedCitations(UUID jobId, int attempt, JsonNode citations) {
        if (!isVerified(jobId, attempt, citations)) {
            throw new JobDeliveryException("Citations carry no verification for this review attempt");
        }
    }

    /** Whether every citation carries this attempt's verdict over the identity and quote it has now. */
    public static boolean isVerified(UUID jobId, int attempt, JsonNode citations) {
        if (!citations.isArray() || citations.isEmpty()) return false;
        for (JsonNode citation : citations) {
            JsonNode verdict = citation.path("verification");
            if (!"VERIFIED".equals(verdict.path("status").asString())
                    || !"EXACT_LOCATION".equals(verdict.path("scope").asString())
                    || !citationDigest(citation)
                            .equals(verdict.path("citationSha256").asString())
                    || !jobId.toString().equals(verdict.path("jobId").asString())
                    || attempt != verdict.path("attempt").asInt(-1)
                    || !verdict.path("artifactSha256").asString().matches(SHA256_HEX)
                    || !verdict.path("quoteSha256").asString().matches(SHA256_HEX)) {
                return false;
            }
            if (citation.path("quote").isString()) {
                String quoteDigest = utf8Digest(citation.path("quote").asString());
                if (quoteDigest == null
                        || !quoteDigest.equals(verdict.path("quoteSha256").asString())) return false;
            }
        }
        return true;
    }

    static String citationDigest(JsonNode citation) {
        var identity = MAPPER.createArrayNode();
        identity.add(citation.path("sourceKind").asString());
        identity.add(citation.path("artifactPath").asString());
        identity.add(citation.path("path").asString());
        identity.add(citation.path("side").asString());
        identity.add(citation.path("revision").asString());
        identity.add(citation.path("startLine").asInt(-1));
        identity.add(citation.path("endLine").asInt(citation.path("startLine").asInt(-1)));
        return ProvenanceDigest.sha256Hex(MAPPER.writeValueAsBytes(identity));
    }

    static String quoteDigest(String quote) {
        String digest = utf8Digest(quote);
        if (digest == null) throw new JobDeliveryException("Citation quote is not valid Unicode");
        return digest;
    }

    /** The digest of a quote as a candidate submitted it; null when it has no UTF-8 encoding. */
    static @Nullable String quoteDigestOrNull(String quote) {
        return utf8Digest(quote);
    }

    /** Null for a quote with a lone surrogate, which has no UTF-8 encoding to digest. */
    private static @Nullable String utf8Digest(String quote) {
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(quote));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return ProvenanceDigest.sha256Hex(bytes);
        } catch (CharacterCodingException exception) {
            return null;
        }
    }
}
