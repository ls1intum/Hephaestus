package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.FolderSource;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.Candidate;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateParser;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.CitationCapabilities;
import de.tum.cit.aet.hephaestus.evidence.CitationRequirement;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceContract;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationRefusal;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationVerifier;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CandidateEvidenceVerifierTest extends BaseUnitTest {
    private static final JsonMapper MAPPER = new JsonMapper();
    private static final SourceKind SOURCE = new SourceKind("docs.document.core");
    private static final ItemKind KIND = new ItemKind("docs.document");
    private static final String TEXT = "Before\nExact quote\nAfter\n";
    private final ReviewJob job = new ReviewJob();
    private final JobEvidenceFiles files = mock(JobEvidenceFiles.class);
    private final SourceCatalogRegistry catalogs = mock(SourceCatalogRegistry.class);
    private final CitationVerifier domain = mock(CitationVerifier.class);

    private CandidateEvidenceVerifier verifier(String text) {
        var policy = mock(SourceContract.class);
        when(catalogs.requireSource(new SourceContractVersion("2.0.0"), SOURCE)).thenReturn(policy);
        when(policy.appliesTo(KIND.value())).thenReturn(true);
        when(policy.citation())
                .thenReturn(new CitationCapabilities(CitationRequirement.FORBIDDEN, CitationRequirement.FORBIDDEN));
        when(catalogs.isSourceUsePermitted(
                        new SourceContractVersion("2.0.0"), SOURCE, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
                .thenReturn(true);
        when(domain.kinds()).thenReturn(Set.of(KIND));
        lenient().when(domain.verify(any())).thenReturn(List.of());
        lenient()
                .when(files.verificationDirectory(job))
                .thenReturn(new EvidenceDirectory("inputs/", Path.of("/tmp/citation-test")));
        lenient()
                .when(files.inspect(eq(job), eq("context/body.txt"), any(), any()))
                .thenAnswer(invocation -> {
                    Function<Reader, Object> read = invocation.getArgument(3);
                    return Optional.ofNullable(read.apply(new StringReader(text)));
                });
        return new CandidateEvidenceVerifier(files, catalogs, List.of(domain));
    }

    private ReviewAssignmentSnapshot assignment() {
        var item = new ItemRef(1, 2, KIND, 20L, null, null);
        var practice = mock(PinnedPractice.class);
        return new ReviewAssignmentSnapshot(
                new ReviewAssignmentId(UUID.randomUUID(), 31, 2),
                item,
                new ItemVersion("docs:one", null),
                null,
                "one",
                practice);
    }

    private CapturedEvidence captured(boolean registered) {
        String digest = ProvenanceDigest.sha256Hex(TEXT.getBytes(StandardCharsets.UTF_8));
        var file = new CapturedSource(
                "context/body.txt", "text/plain", digest, TEXT.getBytes(StandardCharsets.UTF_8).length);
        var now = Instant.now();
        var state = new SourceCaptureState.Available(
                SourceContentState.NON_EMPTY,
                SourceCompleteness.COMPLETE,
                new SourceCaptureFacts(now, null, null, "docs:one"),
                List.of());
        return CapturedEvidence.of(new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "a".repeat(64),
                KIND.value(),
                now,
                List.of(new SourceCapture(SOURCE, state, List.of(file))),
                List.of(),
                List.of(new FolderSource(SOURCE, file)),
                registered
                        ? List.of(new CitableText(
                                SOURCE.value(), "document/body", CitationSide.NEW, null, "context/body.txt", digest))
                        : List.of()));
    }

    private Candidate candidate() {
        return CandidateParser.parse(MAPPER.readTree("""
            {"outcome":"MET","summary":"The practice is met","explanation":"The captured body supports the criterion",
             "citations":[{"sourceKind":"docs.document.core","sourcePath":"context/body.txt","path":"document/body",
             "startLine":999,"endLine":999,"quote":"Exact quote"}],
             "coverage":{"source":"claimed","selected":1,"checked":1,"total":1,"complete":true}}
            """));
    }

    @Test
    void derivesLinesFromTheRegisteredTextBeforeDelegatingToTheDomain() {
        var verifier = verifier(TEXT);
        var candidate = candidate();
        verifier.verifyCandidate(job, assignment(), ReviewKind.ITEM, candidate, captured(true));
        assertThat(candidate.citations().getFirst().path("startLine").asInt()).isEqualTo(2);
        assertThat(candidate.citations().getFirst().path("verification").propertyNames())
                .containsExactlyInAnyOrder("status", "artifactSha256", "quoteSha256");
        var check = ArgumentCaptor.forClass(CitationCheck.class);
        verify(domain).verify(check.capture());
        assertThat(check.getValue().citations().getFirst().startLine()).isEqualTo(2);
    }

    @Test
    void refusesAnUnregisteredOrAmbiguousQuote() {
        var verifier = verifier(TEXT);
        assertThatThrownBy(() ->
                        verifier.verifyCandidate(job, assignment(), ReviewKind.ITEM, candidate(), captured(false)))
                .isInstanceOf(CandidateEvidenceVerifier.Unverified.class);
        var ambiguous = verifier(TEXT + "Exact quote");
        assertThatThrownBy(() ->
                        ambiguous.verifyCandidate(job, assignment(), ReviewKind.ITEM, candidate(), captured(true)))
                .isInstanceOf(CandidateEvidenceVerifier.Unverified.class);
    }

    @Test
    void keepsTheDomainsRevisionRefusalDistinctFromGenericQuoteProof() {
        var verifier = verifier(TEXT);
        lenient()
                .when(domain.verify(any()))
                .thenReturn(List.of(
                        new CitationRefusal(0, CandidateRefusal.BASIS_REVISION_UNSUPPORTED, "Another pinned version")));
        assertThatThrownBy(
                        () -> verifier.verifyCandidate(job, assignment(), ReviewKind.ITEM, candidate(), captured(true)))
                .isInstanceOfSatisfying(
                        CandidateEvidenceVerifier.Unverified.class,
                        refusal -> assertThat(refusal.reasonCode()).isEqualTo("basis_revision_unsupported"));
    }
}
