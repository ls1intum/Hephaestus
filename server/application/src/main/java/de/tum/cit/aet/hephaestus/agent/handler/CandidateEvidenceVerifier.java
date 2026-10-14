package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.Candidate;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.evidence.CitationRequirement;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContract;
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
    private final ClasspathSourceCatalogRegistry catalogs;
    private final List<CitationVerifier> verifiers;

    CandidateEvidenceVerifier(
            JobEvidenceFiles files, ClasspathSourceCatalogRegistry catalogs, List<CitationVerifier> verifiers) {
        this.files = files;
        this.catalogs = catalogs;
        this.verifiers = List.copyOf(verifiers);
    }

    static final class Unverified extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final CandidateRefusal refusal;
        private final transient @Nullable ObjectNode failure;

        Unverified(CandidateRefusal refusal, String reason, @Nullable ObjectNode failure) {
            super(reason, null, false, false);
            this.refusal = refusal;
            this.failure = failure;
        }

        CandidateRefusal refusal() {
            return refusal;
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
            SourceContract contract;
            try {
                contract = catalogs.requireSource(source);
            } catch (IllegalArgumentException unknownSource) {
                throw new Unverified(
                        CandidateRefusal.MALFORMED_CANDIDATE,
                        "The cited source is not in the source catalog",
                        citation);
            }
            if (!contract.appliesTo(assignment.item().kind().value())
                    || !catalogs.isSourceUsePermitted(source, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)) {
                throw new Unverified(
                        CandidateRefusal.MALFORMED_CANDIDATE,
                        "This source cannot support the assigned review",
                        citation);
            }
            capability(contract.citation().side(), citation.has("side"), "side");
            capability(contract.citation().revision(), citation.has("revision"), "revision");
            if (matching.isEmpty() && (citation.has("side") || citation.has("revision"))) {
                throw new Unverified(
                        CandidateRefusal.BASIS_REVISION_UNSUPPORTED,
                        "No item verifier supports this citation reference",
                        citation);
            }
            String sourcePath = citation.path("sourcePath").asString();
            try {
                captured.requireFile(source, sourcePath);
            } catch (JobDeliveryException unavailable) {
                throw new Unverified(
                        CandidateRefusal.SOURCE_UNAVAILABLE,
                        "The cited source was not captured at this path",
                        citation);
            }
            String path = citation.path("path").asString();
            CitationSide side = citation.has("side")
                    ? CitationSide.valueOf(citation.path("side").asString())
                    : CitationSide.NEW;
            String revision =
                    citation.has("revision") ? citation.path("revision").asString() : null;
            var proof = captured.resolve(source, path, side, revision);
            String quote = citation.path("quote").asString();
            var text = files.readText(job, proof.stagedPath(), proof.sha256())
                    .orElseThrow(() -> new Unverified(
                            CandidateRefusal.SOURCE_UNAVAILABLE, "Captured source text is unavailable", citation));
            int start = quote.isEmpty() ? -1 : text.indexOf(quote);
            if (start < 0 || text.indexOf(quote, start + 1) >= 0) {
                throw new Unverified(
                        CandidateRefusal.QUOTE_UNVERIFIED,
                        "The quote is absent or ambiguous in the captured text",
                        citation);
            }
            int firstLine = 1;
            for (int offset = 0; offset < start; offset++) if (text.charAt(offset) == '\n') firstLine++;
            int lastLine = firstLine;
            for (int offset = 0; offset < quote.length() - 1; offset++) if (quote.charAt(offset) == '\n') lastLine++;
            citation.put("startLine", firstLine);
            citation.put("endLine", lastLine);
            var verification = citation.putObject("verification");
            verification.put("status", "VERIFIED");
            verification.put("itemSha256", proof.sha256());
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

            switch (problem.basisType()) {
                case "state" -> {
                    if (scope != ReviewKind.SCAN)
                        throw new Unverified(
                                CandidateRefusal.BASIS_STATE_UNSUPPORTED,
                                "State claims are supported only in a scan",
                                null);
                    String itemPath = assignment.item().path();
                    if (itemPath == null
                            || problem.citations().stream()
                                    .noneMatch(citation -> itemPath.equals(
                                            citation.path("path").asString()))) {
                        throw new Unverified(
                                CandidateRefusal.BASIS_WITNESS_MISSING,
                                "A state claim needs a witness from its assigned file",
                                null);
                    }
                }
                case "duty" -> {
                    String quote = problem.basis().path("criterionQuote").asString();
                    if (quote.isBlank()
                            || !assignment.practice().definition().criteria().contains(quote)) {
                        throw new Unverified(
                                CandidateRefusal.DUTY_QUOTE_UNVERIFIED,
                                "The duty quote is absent from the pinned criteria",
                                null);
                    }
                }
                case "introduced", "worsened" -> {
                    if (scope == ReviewKind.SCAN)
                        throw new Unverified(
                                CandidateRefusal.BASIS_STATE_UNSUPPORTED, "A scan requires a state basis", null);
                    if (matching.isEmpty())
                        throw new Unverified(
                                CandidateRefusal.BASIS_REVISION_UNSUPPORTED,
                                "No item verifier supports change-basis references",
                                null);
                    int base = citationOffset;
                    int headIndex = base + problem.basis().path("head").asInt();
                    Integer baseIndex = problem.basisType().equals("worsened")
                            ? base + problem.basis().path("base").asInt()
                            : null;
                    bases.add(new BasisToCheck(i, problem.basisType(), headIndex, baseIndex));
                }
                default -> throw new Unverified(CandidateRefusal.MALFORMED_CANDIDATE, "Unknown problem basis", null);
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
                throw new Unverified(refusal.reason(), refusal.message(), failure);
            }
        }
    }

    private static void capability(CitationRequirement requirement, boolean present, String field) {
        if ((requirement == CitationRequirement.REQUIRED && !present)
                || (requirement == CitationRequirement.FORBIDDEN && present)) {
            throw new Unverified(
                    CandidateRefusal.MALFORMED_CANDIDATE,
                    "The source catalog does not permit this " + field + " shape",
                    null);
        }
    }

    private static void requireConsulted(@Nullable ObjectNode warrant, CapturedEvidence captured) {
        if (warrant == null) return;
        for (var source : warrant.path("consulted")) {
            if (!captured.availableSources().contains(new SourceKind(source.asString()))) {
                throw new Unverified(
                        CandidateRefusal.SOURCE_UNAVAILABLE, "A warrant names a source that was not captured", null);
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
