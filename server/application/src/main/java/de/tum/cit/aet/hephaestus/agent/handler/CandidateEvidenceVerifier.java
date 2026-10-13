package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService.BASIS_STATE_UNSUPPORTED;
import static de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService.DUTY_NOT_IN_CRITERIA;
import static de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService.QUOTE_UNVERIFIED;
import static de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService.UNAUTHORIZED_SOURCE;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.context.HistoricalGitEvidence;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.RepositoryTreeContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.Candidate;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateProblem;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateRefusal;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.EvidenceStance;
import de.tum.cit.aet.hephaestus.practices.PracticePreconditionClause;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The evidence checks of submitted candidates: every cited source was staged, every quote is the exact text at its
 * place in the captured source, and every warrant consults only what the run staged. {@link ReviewOutputService}
 * decides what each outcome does to the submission.
 */
@Component
class CandidateEvidenceVerifier {

    private static final String CONVERSATION_THREAD = "context/conversation_thread.json";

    /**
     * The captured witness of a checkout that a repository citation names ({@code CHECKOUT_WITNESS} in
     * {@code pi-result-normalize.ts}, which refuses the same citations in the review session).
     */
    static final Pattern CHECKOUT_WITNESS = Pattern.compile("repos/[a-zA-Z0-9_-]+/\\.git/HEAD");

    private final ObjectMapper objectMapper;
    private final JobEvidenceFiles evidenceFiles;
    private final HistoricalGitEvidence historicalGit;
    private final CitedSourceAccess citedSourceAccess;

    CandidateEvidenceVerifier(
            ObjectMapper objectMapper,
            JobEvidenceFiles evidenceFiles,
            HistoricalGitEvidence historicalGit,
            CitedSourceAccess citedSourceAccess) {
        this.objectMapper = objectMapper;
        this.evidenceFiles = evidenceFiles;
        this.historicalGit = historicalGit;
        this.citedSourceAccess = citedSourceAccess;
    }

    /** A quote, a place or a basis that does not verify: it withholds its own practice, never the run. */
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

    /** The two repository sources a quote of code can name: the checkout, and the change within it. */
    private static boolean isCodeSource(SourceKind kind) {
        return RepositoryTreeContentSource.KIND.equals(kind) || PracticePreconditionClause.DIFF_SOURCE.equals(kind);
    }

    /** One citation of a candidate, with its JSON path in the candidate. */
    record Located(String path, ObjectNode citation) {}

    /** Every citation of a candidate: the result's, then each problem's, the example's and each open question's. */
    static List<Located> located(Candidate candidate) {
        List<Located> all = new ArrayList<>();
        for (int index = 0; index < candidate.citations().size(); index++) {
            all.add(new Located(
                    "citations[" + index + "]", candidate.citations().get(index)));
        }
        for (int problem = 0; problem < candidate.problems().size(); problem++) {
            List<ObjectNode> citations = candidate.problems().get(problem).citations();
            for (int index = 0; index < citations.size(); index++) {
                all.add(new Located("problems[" + problem + "].citations[" + index + "]", citations.get(index)));
            }
        }
        ObjectNode example = candidate.example();
        if (example != null && example.get("citation") instanceof ObjectNode citation) {
            all.add(new Located("example.citation", citation));
        }
        for (int question = 0; question < candidate.openQuestions().size(); question++) {
            if (candidate.openQuestions().get(question).get("citation") instanceof ObjectNode citation) {
                all.add(new Located("evidence.undecidability[" + question + "].citation", citation));
            }
        }
        return all;
    }

    /**
     * Every quote of code, verified in one pass against the attempt's checkout. A quote from the change is a quote
     * from the head (NEW) or the base (OLD) of the pinned range, at a path the change touches.
     */
    CodeQuotes verifyCodeQuotes(AgentJob job, Collection<Candidate> candidates, CapturedEvidence captured) {
        List<JsonNode> code = new ArrayList<>();
        boolean citesChange = false;
        for (Candidate candidate : candidates) {
            for (Located cited : located(candidate)) {
                String kind = cited.citation().path("sourceKind").asString();
                if (RepositoryTreeContentSource.KIND.value().equals(kind)
                        || PracticePreconditionClause.DIFF_SOURCE.value().equals(kind)) {
                    code.add(cited.citation());
                    citesChange |=
                            PracticePreconditionClause.DIFF_SOURCE.value().equals(kind);
                }
            }
        }
        if (code.isEmpty()) return new CodeQuotes(Map.of(), Set.of(), Map.of());
        Map<String, List<JsonNode>> groups;
        try {
            groups = code.stream().collect(Collectors.groupingBy(CandidateEvidenceVerifier::repositoryRoot));
        } catch (JobDeliveryException unauthorized) {
            throw new ResultsRefusedException(
                    UNAUTHORIZED_SOURCE, String.valueOf(unauthorized.getMessage()), unauthorized);
        }
        var matches = new HashMap<HistoricalGitEvidence.Citation, JobEvidenceFiles.QuoteMatch>();
        var heads = new HashMap<String, String>();
        Set<String> changedPaths = Set.of();
        for (var group : groups.entrySet()) {
            String root = group.getKey();
            CapturedEvidence.Artifact head;
            CapturedEvidence.Artifact refs;
            try {
                head = captured.requireArtifact(RepositoryTreeContentSource.KIND, root + ".git/HEAD");
                refs = captured.requireArtifact(
                        RepositoryTreeContentSource.KIND, root + ".git/hephaestus-captured-refs");
            } catch (JobDeliveryException unauthorized) {
                throw new ResultsRefusedException(
                        UNAUTHORIZED_SOURCE, String.valueOf(unauthorized.getMessage()), unauthorized);
            }
            String pinnedHead = root.equals(SandboxLayout.REPO_MOUNT_RELATIVE)
                    ? captured.pinnedHead()
                    : evidenceFiles
                            .inspect(job, root + ".git/HEAD", head.sha256(), reader -> {
                                String identity = new BufferedReader(reader).readLine();
                                if (identity == null || !identity.matches(CitationVerification.GIT_OBJECT_ID))
                                    throw new JobDeliveryException("Captured repository has no pinned head");
                                return identity;
                            })
                            .orElseThrow(() -> new JobDeliveryException("Captured repository is unavailable"));
            heads.put(root, pinnedHead);
            List<HistoricalGitEvidence.Citation> requested = new ArrayList<>();
            for (JsonNode citation : group.getValue()) {
                HistoricalGitEvidence.Citation asked = codeCitation(citation, captured, pinnedHead);
                if (asked != null) requested.add(asked);
            }
            if (citesChange && root.equals(SandboxLayout.REPO_MOUNT_RELATIVE)) {
                String[] range = captured.reviewRange();
                changedPaths = historicalGit.changedPaths(job, head.sha256(), refs.sha256(), range[0], range[1]);
            }
            matches.putAll(
                    root.equals(SandboxLayout.REPO_MOUNT_RELATIVE)
                            ? historicalGit.verifyAll(job, head.sha256(), refs.sha256(), pinnedHead, requested)
                            : historicalGit.verifyAllAt(
                                    job, root, head.sha256(), refs.sha256(), pinnedHead, requested));
        }
        return new CodeQuotes(Map.copyOf(matches), changedPaths, Map.copyOf(heads));
    }

    private static String repositoryRoot(JsonNode citation) {
        if (PracticePreconditionClause.DIFF_SOURCE
                .value()
                .equals(citation.path("sourceKind").asString())) return SandboxLayout.REPO_MOUNT_RELATIVE;
        String artifact = citation.path("artifactPath").asString();
        if (!CHECKOUT_WITNESS.matcher(artifact).matches())
            throw new JobDeliveryException("A repository citation must name its captured HEAD witness");
        return artifact.substring(0, artifact.length() - ".git/HEAD".length());
    }

    record CodeQuotes(
            Map<HistoricalGitEvidence.Citation, JobEvidenceFiles.QuoteMatch> matches,
            Set<String> changedPaths,
            Map<String, String> pinnedHeads) {}

    /**
     * A quote of code as a revision, a path and a line range; null for a change citation that names another revision
     * than its side's, which then fails its own verification. A checkout citation names the captured {@code .git/HEAD}
     * and may select a revision; a change citation names the pinned change and a side, which selects its revision.
     */
    private static HistoricalGitEvidence.@Nullable Citation codeCitation(
            JsonNode citation, CapturedEvidence captured, String pinnedHead) {
        boolean change = PracticePreconditionClause.DIFF_SOURCE
                .value()
                .equals(citation.path("sourceKind").asString());
        String expectedArtifact =
                change ? PullRequestContentSource.CHANGE_FILE : repositoryRoot(citation) + ".git/HEAD";
        String revision;
        if (change) {
            String[] range = captured.reviewRange();
            revision = citation.path("side").asString("").equals("OLD") ? range[0] : range[1];
            if (citation.has("revision")
                    && !revision.equals(citation.path("revision").asString())) return null;
        } else {
            revision = citation.path("revision").asString(pinnedHead);
        }
        String path = citation.path("path").asString();
        String quote = citation.path("quote").asString();
        int start = citation.path("startLine").asInt(-1);
        int end = citation.path("endLine").asInt(start);
        if (!expectedArtifact.equals(citation.path("artifactPath").asString())
                || !revision.matches(CitationVerification.GIT_OBJECT_ID)) {
            throw new ResultsRefusedException(
                    UNAUTHORIZED_SOURCE,
                    "Invalid code citation: name the captured artifact, a relative path and an exact line" + " range");
        }
        CitationVerification.quoteDigest(quote);
        return new HistoricalGitEvidence.Citation(revision, path, quote, start, end);
    }

    /**
     * Every check of one candidate besides its shape. A quote that does not verify, a duty its criteria do not state,
     * or a basis admission cannot check withholds the practice ({@link Unverified}); a source the run did not stage or
     * a warrant that claims one refuses the submission.
     */
    void verifyCandidate(
            String slug,
            Candidate candidate,
            PracticeRevision revision,
            CapturedEvidence captured,
            AgentJob job,
            CodeQuotes codeQuotes) {
        if (revision.getAutomatedReviewPolicy() == null
                || revision.getSignals() == null
                || revision.getEvidenceRequirements() == null) {
            throw new JobDeliveryException(
                    "Practice has no evidence requirements: slug=" + slug + ", jobId=" + job.getId());
        }
        // Citations may use any staged source, not only the practice's evidence requirements.
        // EXHAUSTIVE requirements bound absence claims; byte-exact quotes establish citation validity.
        Set<SourceKind> exhaustive = new HashSet<>();
        revision.getEvidenceRequirements().forEach(need -> {
            if (need.stance() == EvidenceStance.EXHAUSTIVE) {
                exhaustive.add(need.sourceKind());
            }
        });
        enforceRecordedSearch(slug, candidate, exhaustive, captured, job);
        enforceStatedInapplicability(slug, candidate, captured, job);
        for (Located cited : located(candidate)) {
            verifyCitation(slug, cited, captured, job, codeQuotes);
        }
        ObjectNode example = candidate.example();
        if (example != null) {
            JsonNode exampleCitation = example.path("citation");
            for (CandidateProblem problem : candidate.problems()) {
                for (ObjectNode citation : problem.citations()) {
                    if (codeLocationsOverlap(citation, exampleCitation)) {
                        throw new Unverified(
                                CandidateRefusal.EXAMPLE_OVERLAPS_PROBLEM.code(),
                                "The example cites the place of a problem",
                                null);
                    }
                }
            }
        }
        for (CandidateProblem problem : candidate.problems()) {
            switch (problem.basisType()) {
                case "state" ->
                    throw new Unverified(
                            BASIS_STATE_UNSUPPORTED,
                            "A problem rests on the state of the work, which this review cannot judge yet",
                            null);
                case "duty" -> {
                    String quoted =
                            normalized(problem.basis().path("criterionQuote").asString());
                    if (quoted.isEmpty() || !normalized(revision.getCriteria()).contains(quoted)) {
                        throw new Unverified(
                                DUTY_NOT_IN_CRITERIA,
                                "A problem rests on a duty that the practice's criteria do not state",
                                null);
                    }
                }
                case "introduced", "worsened" -> {
                    ObjectNode head =
                            problem.citations().get(problem.basis().path("head").asInt());
                    boolean contradictory = contradictsCodeBasis(head, captured, true);
                    if (problem.basisType().equals("worsened")) {
                        ObjectNode base = problem.citations()
                                .get(problem.basis().path("base").asInt());
                        contradictory |= contradictsCodeBasis(base, captured, false);
                    }
                    if (contradictory) {
                        throw new Unverified(
                                BASIS_STATE_UNSUPPORTED,
                                "The problem's basis cites another repository or revision than the reviewed change",
                                null);
                    }
                }
                default -> {}
            }
        }
        if (job.getJobType() == AgentJobType.CONVERSATION_REVIEW && candidate.outcome() == Outcome.NOT_MET) {
            for (int index = 0; index < candidate.problems().size(); index++) {
                List<ObjectNode> citations = candidate.problems().get(index).citations();
                if (!citesReviewedTurn(job, citations, captured)) {
                    throw new Unverified(
                            QUOTE_UNVERIFIED,
                            "A problem of a conversation review cites no turn of the participant under review",
                            failure(
                                    "problems[" + index + "].citations[0]",
                                    citations.getFirst(),
                                    "NOT_THE_REVIEWED_PARTICIPANT"));
                }
            }
        }
    }

    private static boolean contradictsCodeBasis(JsonNode citation, CapturedEvidence captured, boolean head) {
        if (!isCodeSource(new SourceKind(citation.path("sourceKind").asString()))) return false;
        String expectedRevision = head ? captured.pinnedHead() : captured.reviewRange()[0];
        return !SandboxLayout.REPO_MOUNT_RELATIVE.equals(repositoryRoot(citation))
                || !expectedRevision.equals(citation.path("revision").asString());
    }

    private static boolean codeLocationsOverlap(JsonNode a, JsonNode b) {
        return isCodeSource(new SourceKind(a.path("sourceKind").asString()))
                && isCodeSource(new SourceKind(b.path("sourceKind").asString()))
                && repositoryRoot(a).equals(repositoryRoot(b))
                && a.path("revision").asString().equals(b.path("revision").asString())
                && a.path("path").asString().equals(b.path("path").asString())
                && a.path("startLine").asInt() <= b.path("endLine").asInt()
                && b.path("startLine").asInt() <= a.path("endLine").asInt();
    }

    private static String normalized(@Nullable String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private ObjectNode failure(String path, JsonNode citation, String reasonCode) {
        ObjectNode failure = objectMapper.createObjectNode();
        failure.put("citation", path);
        failure.put("status", "REJECTED");
        failure.put("reasonCode", reasonCode);
        failure.put("citationSha256", CitationVerification.citationDigest(citation));
        String quoteDigest = citation.path("quote").isString()
                ? CitationVerification.quoteDigestOrNull(citation.path("quote").asString())
                : null;
        if (quoteDigest != null) failure.put("candidateQuoteSha256", quoteDigest);
        return failure;
    }

    private void verifyCitation(
            String slug, Located cited, CapturedEvidence captured, AgentJob job, CodeQuotes codeQuotes) {
        ObjectNode citation = cited.citation();
        SourceKind kind;
        try {
            kind = new SourceKind(citation.path("sourceKind").asString());
        } catch (IllegalArgumentException e) {
            throw new ResultsRefusedException(
                    UNAUTHORIZED_SOURCE, "A result names an invalid evidence source: slug=" + slug, e);
        }
        String artifactPath = citation.path("artifactPath").asString();
        CapturedEvidence.Artifact artifact = captured.artifact(artifactPath);
        if (!captured.availableSources().contains(kind)
                || artifact == null
                || !artifact.kind().equals(kind)) {
            throw new ResultsRefusedException(
                    UNAUTHORIZED_SOURCE,
                    "A result cited an unavailable or misattributed evidence source " + kind + ": slug=" + slug
                            + ", jobId=" + job.getId());
        }
        int startLine = citation.path("startLine").asInt();
        int endLine = citation.path("endLine").asInt(startLine);
        String exactQuote = citation.path("quote").asString("");
        String quoteDigest = CitationVerification.quoteDigest(exactQuote);
        // The candidate contract already refuses a revision or a side on any other source.
        if (isCodeSource(kind)) {
            HistoricalGitEvidence.Citation requested = codeCitation(
                    citation,
                    captured,
                    Objects.requireNonNull(codeQuotes.pinnedHeads().get(repositoryRoot(citation))));
            if (requested == null) {
                throw new Unverified(
                        QUOTE_UNVERIFIED,
                        "Cited revision is not this side of the reviewed change",
                        failure(cited.path(), citation, "QUOTE_LOCATION_MISMATCH"));
            }
            citation.put("revision", requested.revision());
            if (PracticePreconditionClause.DIFF_SOURCE.equals(kind)
                    && !codeQuotes.changedPaths().contains(requested.path())) {
                throw new Unverified(
                        QUOTE_UNVERIFIED,
                        "Cited path is not touched by the reviewed change",
                        failure(cited.path(), citation, "QUOTE_LOCATION_MISMATCH"));
            }
            var match = codeQuotes.matches().get(requested);
            if (match == null) throw new JobDeliveryException("Code citation has no prepared verification");
            String blobDigest = match.artifactSha256();
            if (blobDigest == null) {
                throw new Unverified(
                        QUOTE_UNVERIFIED,
                        "Cited path does not exist at the cited revision",
                        failure(cited.path(), citation, "QUOTE_LOCATION_MISMATCH"));
            }
            if (!match.matches()) {
                throw new Unverified(
                        QUOTE_UNVERIFIED,
                        "Quote does not match the cited revision and lines",
                        failure(cited.path(), citation, "QUOTE_LOCATION_MISMATCH"));
            }
            citedSourceAccess.bind(job, citation, artifact.sha256());
            CitationVerification.record(citation, job, blobDigest, quoteDigest);
            return;
        }
        // The path of a serialized source is a label for the reader; the artifact and its lines
        // are what the quote is verified against.
        boolean containsQuote = evidenceFiles
                .containsUtf8AtLines(job, artifactPath, artifact.sha256(), exactQuote, startLine, endLine)
                .orElseThrow(() -> new JobDeliveryException("Cited evidence artifact is no longer available: path="
                        + artifactPath + ", jobId=" + job.getId()));
        if (!containsQuote) {
            throw new Unverified(
                    QUOTE_UNVERIFIED,
                    "Quote does not occur in the cited artifact at the cited lines",
                    failure(cited.path(), citation, "QUOTE_LOCATION_MISMATCH"));
        }
        citedSourceAccess.bind(job, citation, artifact.sha256());
        CitationVerification.record(citation, job, artifact.sha256(), quoteDigest);
    }

    /**
     * A conversation is reviewed once for each participant, so a lapse must rest on a turn that participant wrote:
     * a citation inside a turn that {@code conversation_thread.json} marks {@code underReview}.
     */
    private boolean citesReviewedTurn(AgentJob job, List<ObjectNode> citations, CapturedEvidence captured) {
        CapturedEvidence.Artifact thread = captured.artifact(CONVERSATION_THREAD);
        if (thread == null) return false;
        List<int[]> turns = evidenceFiles
                .inspect(job, CONVERSATION_THREAD, thread.sha256(), this::reviewedTurnLines)
                .orElseThrow(() -> new JobDeliveryException("The cited conversation is unavailable"));
        for (JsonNode citation : citations) {
            if (!CONVERSATION_THREAD.equals(citation.path("artifactPath").asString())) continue;
            int first = citation.path("startLine").asInt();
            int last = citation.path("endLine").asInt(first);
            for (int[] turn : turns) {
                if (turn[0] <= first && last <= turn[1]) return true;
            }
        }
        return false;
    }

    /** The first and last line of each turn marked {@code underReview}, as the record is written. */
    private List<int[]> reviewedTurnLines(Reader input) {
        List<int[]> turns = new ArrayList<>();
        try (JsonParser parser = objectMapper
                .reader()
                .without(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) return turns;
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = parser.currentName();
                if (parser.nextToken() != JsonToken.START_ARRAY || !"messages".equals(name)) {
                    parser.skipChildren();
                    continue;
                }
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    int first = parser.currentTokenLocation().getLineNr();
                    boolean underReview = false;
                    while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                        String field = parser.currentName();
                        JsonToken value = parser.nextToken();
                        if ("underReview".equals(field)) underReview = value == JsonToken.VALUE_TRUE;
                        else parser.skipChildren();
                    }
                    if (underReview)
                        turns.add(
                                new int[] {first, parser.currentTokenLocation().getLineNr()});
                }
            }
        }
        return turns;
    }

    /** Requires a NOT_APPLICABLE result's warrant to consult only sources this run staged. */
    private static void enforceStatedInapplicability(
            String slug, Candidate candidate, CapturedEvidence captured, AgentJob job) {
        ObjectNode inapplicability = candidate.inapplicability();
        if (inapplicability == null) return;
        requireStaged(slug, inapplicability.path("consulted"), captured, job, "Stated inapplicability");
    }

    /** Validates a recorded absence search against the staged sources and declared corpus. */
    private static void enforceRecordedSearch(
            String slug, Candidate candidate, Set<SourceKind> exhaustive, CapturedEvidence captured, AgentJob job) {
        ObjectNode search = candidate.search();
        if (search == null) return;
        if (candidate.outcome() == Outcome.MET && exhaustive.isEmpty()) {
            throw new JobDeliveryException(
                    "A MET absence claim needs a practice that bounds the corpus it searches, and this one "
                            + "declares no EXHAUSTIVE evidence source: slug="
                            + slug
                            + ", jobId="
                            + job.getId());
        }
        Set<SourceKind> searched = requireStaged(slug, search.path("consulted"), captured, job, "Recorded search");
        if (!searched.containsAll(exhaustive)) {
            Set<SourceKind> unsearched = new HashSet<>(exhaustive);
            unsearched.removeAll(searched);
            throw new JobDeliveryException(
                    "An absence claim did not search the sources its practice asserts absence over "
                            + unsearched
                            + ": slug="
                            + slug
                            + ", jobId="
                            + job.getId());
        }
    }

    /**
     * The consulted sources of a warrant. A source not staged for this run cannot have been searched or read, so
     * claiming otherwise is fabrication either way.
     */
    private static Set<SourceKind> requireStaged(
            String slug, JsonNode consulted, CapturedEvidence captured, AgentJob job, String warrant) {
        Set<SourceKind> kinds = new HashSet<>();
        for (JsonNode kind : consulted) {
            SourceKind sourceKind;
            try {
                sourceKind = new SourceKind(kind.asString());
            } catch (IllegalArgumentException e) {
                throw new ResultsRefusedException(
                        UNAUTHORIZED_SOURCE, warrant + " names an invalid source: slug=" + slug, e);
            }
            if (!captured.availableSources().contains(sourceKind)) {
                throw new ResultsRefusedException(
                        UNAUTHORIZED_SOURCE,
                        warrant + " claims a source this run did not stage " + sourceKind + ": slug=" + slug
                                + ", jobId=" + job.getId());
            }
            kinds.add(sourceKind);
        }
        return kinds;
    }
}
