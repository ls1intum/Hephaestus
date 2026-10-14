package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.Candidate;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.evidence.CitationRequirement;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.BasisToCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationToCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationVerifier;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/** Generic capture checks followed by the item owner's side, revision and change-basis checks. */
@Component
class CandidateEvidenceVerifier {
    private final JobEvidenceFiles files;
    private final SourceCatalogRegistry catalogs;
    private final List<CitationVerifier> verifiers;

    CandidateEvidenceVerifier(
            JobEvidenceFiles files, SourceCatalogRegistry catalogs, List<CitationVerifier> verifiers) {
        this.files = files;
        this.catalogs = catalogs;
        this.verifiers = List.copyOf(verifiers);
    }

    static final class Unverified extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String reasonCode;
        private final transient @Nullable ObjectNode failure;

        Unverified(String reasonCode, String reason, @Nullable ObjectNode failure) {
            super(reason, null, false, false);
            this.reasonCode = reasonCode;
            this.failure = failure;
        }

        String reasonCode() {
            return reasonCode;
        }

        @Nullable
        ObjectNode failure() {
            return failure;
        }
    }

    void verifyCandidate(
            ReviewJob job,
            ReviewAssignmentSnapshot assignment,
            ReviewKind scope,
            Candidate candidate,
            CapturedEvidence captured) {
        var matching = verifiers.stream()
                .filter(verifier -> verifier.kinds().contains(assignment.item().kind()))
                .toList();
        if (matching.size() > 1) throw new JobDeliveryException("Multiple citation verifiers handle this item kind");
        List<ObjectNode> located = citations(candidate);
        List<CitationToCheck> checks = new ArrayList<>();
        for (int i = 0; i < located.size(); i++) {
            ObjectNode citation = located.get(i);
            SourceKind source = new SourceKind(citation.path("sourceKind").asString());
            var contract = catalogs.requireSource(captured.contractVersion(), source);
            if (!contract.appliesTo(assignment.item().kind().value())
                    || !catalogs.isSourceUsePermitted(
                            captured.contractVersion(), source, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)) {
                throw new ResultsRefusedException(
                        "unauthorized_source", "This source cannot support the assigned review");
            }
            capability(contract.citation().side(), citation.has("side"), "side");
            capability(contract.citation().revision(), citation.has("revision"), "revision");
            if (matching.isEmpty() && (citation.has("side") || citation.has("revision"))) {
                throw new Unverified(
                        CandidateRefusal.QUOTE_UNVERIFIED.wireValue(),
                        "No item verifier supports this citation reference",
                        citation);
            }
            String sourcePath = citation.path("sourcePath").asString();
            captured.requireFile(source, sourcePath);
            String path = citation.path("path").asString();
            CitationSide side = citation.has("side")
                    ? CitationSide.valueOf(citation.path("side").asString())
                    : CitationSide.NEW;
            String revision =
                    citation.has("revision") ? citation.path("revision").asString() : null;
            var proof = captured.resolve(source, path, side, revision);
            String quote = citation.path("quote").asString();
            var text = files.inspect(job, proof.stagedPath(), proof.sha256(), reader -> {
                        var out = new StringBuilder();
                        char[] buffer = new char[8192];
                        for (int size; (size = reader.read(buffer)) != -1; ) {
                            if (out.length() + size > 32 * 1024 * 1024)
                                throw new JobDeliveryException("Captured text exceeds its read bound");
                            out.append(buffer, 0, size);
                        }
                        return out.toString();
                    })
                    .orElseThrow(
                            () -> new Unverified("quote_unverified", "Captured source text is unavailable", citation));
            int start = quote.isEmpty() ? -1 : text.indexOf(quote);
            if (start < 0 || text.indexOf(quote, start + 1) >= 0) {
                throw new Unverified(
                        "quote_unverified", "The quote is absent or ambiguous in the captured text", citation);
            }
            int firstLine = 1;
            for (int offset = 0; offset < start; offset++) if (text.charAt(offset) == '\n') firstLine++;
            int lastLine = firstLine;
            for (int offset = 0; offset < quote.length() - 1; offset++) if (quote.charAt(offset) == '\n') lastLine++;
            citation.put("startLine", firstLine);
            citation.put("endLine", lastLine);
            var verification = citation.putObject("verification");
            verification.put("status", "VERIFIED");
            verification.put("artifactSha256", proof.sha256());
            verification.put("quoteSha256", ProvenanceDigest.sha256Hex(quote.getBytes(StandardCharsets.UTF_8)));
            checks.add(new CitationToCheck(
                    i, source.value(), citation.path("path").asString(), side, revision, firstLine, lastLine, quote));
        }
        requireConsulted(candidate.search(), captured);
        requireConsulted(candidate.inapplicability(), captured);
        List<BasisToCheck> bases = new ArrayList<>();
        int citationOffset = candidate.citations().size();
        for (int i = 0; i < candidate.problems().size(); i++) {
            var problem = candidate.problems().get(i);
            if (scope == ReviewKind.SCAN && !problem.basisType().equals("state")) {
                throw new Unverified(
                        CandidateRefusal.BASIS_STATE_UNSUPPORTED.wireValue(), "A scan requires a state basis", null);
            }
            switch (problem.basisType()) {
                case "state" -> {
                    if (scope != ReviewKind.SCAN)
                        throw new Unverified(
                                CandidateRefusal.BASIS_STATE_UNSUPPORTED.wireValue(),
                                "State claims are supported only in a scan",
                                null);
                    String itemPath = assignment.item().path();
                    if (itemPath == null
                            || problem.citations().stream()
                                    .noneMatch(citation -> itemPath.equals(
                                            citation.path("path").asString()))) {
                        throw new Unverified(
                                CandidateRefusal.BASIS_REVISION_UNSUPPORTED.wireValue(),
                                "A state claim needs a witness from its assigned file",
                                null);
                    }
                }
                case "duty" -> {
                    String quote = problem.basis().path("criterionQuote").asString();
                    if (quote.isBlank()
                            || !assignment.practice().definition().criteria().contains(quote)) {
                        throw new Unverified(
                                CandidateRefusal.WARRANT_OUTCOME_MISMATCH.wireValue(),
                                "The duty quote is absent from the pinned criteria",
                                null);
                    }
                }
                case "introduced", "worsened" -> {
                    if (scope == ReviewKind.SCAN)
                        throw new Unverified(
                                CandidateRefusal.BASIS_STATE_UNSUPPORTED.wireValue(),
                                "A scan requires a state basis",
                                null);
                    if (matching.isEmpty())
                        throw new Unverified(
                                CandidateRefusal.BASIS_REVISION_UNSUPPORTED.wireValue(),
                                "No item verifier supports change-basis references",
                                null);
                    int base = citationOffset;
                    int headIndex = base + problem.basis().path("head").asInt();
                    Integer baseIndex = problem.basisType().equals("worsened")
                            ? base + problem.basis().path("base").asInt()
                            : null;
                    bases.add(new BasisToCheck(i, problem.basisType(), headIndex, baseIndex));
                }
                default -> throw new ResultsRefusedException("malformed_candidate", "Unknown problem basis");
            }
            citationOffset += problem.citations().size();
        }
        if (!matching.isEmpty()) {
            var version = assignment.version();
            var refusals = matching.getFirst()
                    .verify(new CitationCheck(
                            job.getWorkspaceId(),
                            assignment.item(),
                            version,
                            version.baseVersion() == null ? null : new ItemVersion(version.baseVersion(), null),
                            files.verificationDirectory(job),
                            checks,
                            bases));
            if (!refusals.isEmpty()) {
                var refusal = refusals.getFirst();
                ObjectNode failure =
                        refusal.index() >= 0 && refusal.index() < located.size() ? located.get(refusal.index()) : null;
                throw new Unverified(refusal.reason().wireValue(), refusal.message(), failure);
            }
        }
    }

    private static void capability(CitationRequirement requirement, boolean present, String field) {
        if ((requirement == CitationRequirement.REQUIRED && !present)
                || (requirement == CitationRequirement.FORBIDDEN && present)) {
            throw new ResultsRefusedException(
                    "malformed_candidate", "The source catalog does not permit this " + field + " shape");
        }
    }

    private static void requireConsulted(@Nullable ObjectNode warrant, CapturedEvidence captured) {
        if (warrant == null) return;
        for (var source : warrant.path("consulted")) {
            if (!captured.availableSources().contains(new SourceKind(source.asString()))) {
                throw new ResultsRefusedException(
                        "unauthorized_source", "A warrant names a source that was not captured");
            }
        }
    }

    static List<ObjectNode> citations(Candidate candidate) {
        var citations = new ArrayList<>(candidate.citations());
        candidate.problems().forEach(problem -> citations.addAll(problem.citations()));
        if (candidate.example() != null)
            citations.add((ObjectNode) candidate.example().get("citation"));
        candidate.openQuestions().forEach(question -> {
            if (question.has("citation")) citations.add((ObjectNode) question.get("citation"));
        });
        return List.copyOf(citations);
    }
}
