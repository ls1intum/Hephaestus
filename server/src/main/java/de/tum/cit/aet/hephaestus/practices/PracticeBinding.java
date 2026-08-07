package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One occasion on which a practice is reviewed, and the evidence a review occasioned that way needs.
 *
 * <p>This replaced three separate statements a practice used to make — its artifact kind, the events
 * that started a review, and the named evidence policy the review read under. The first was stated
 * three times and derivable from the third; the last was shared across practices, so adding a source
 * to one practice was a mutation invisible in that practice's diff.
 *
 * <p>Evidence belongs here rather than on the practice because <em>what a review needs is a function
 * of what occasioned it</em>: a review that runs when a change merges may have to establish that no
 * decision was ever recorded, while the same practice reviewed when the change was opened is only
 * reading what is in front of it. It is also the only shape in which one practice can watch both sides
 * of a question — "a change merged with no decision recorded" needs a binding on the change and a
 * binding on the document, each with its own evidence.
 *
 * @param signals  the signals that occasion this review; at least one, and all of one artifact kind
 * @param needs    the sources a review occasioned this way reads, with the stance it takes to each
 * @param onDrafts whether an artifact still marked draft occasions this review. Defaults to false —
 *                 most practices judge work that has been handed over — and is the per-binding
 *                 replacement for the fleet-wide draft veto that made every draft-specific criterion
 *                 in the catalog unreachable
 */
@Schema(description = "An occasion that starts a review, and the evidence that review reads")
public record PracticeBinding(
    @NonNull
    @NotEmpty(message = "A binding must name at least one signal")
    @Schema(description = "Signals that occasion this review, e.g. scm.pull_request.merged")
    List<SignalName> signals,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Sources a review occasioned this way reads, each with the stance it takes")
    List<PracticeEvidenceRequirement> needs,
    @Schema(
        description = "Whether an artifact still marked draft occasions this review",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    boolean onDrafts
) {
    public PracticeBinding {
        // Sorted and de-duplicated on construction for the same reason the needs list is: these values
        // are digested into the review-rule fingerprint, and two authors writing the same binding in a
        // different order must not read as two different rules.
        signals = List.copyOf(
            new LinkedHashSet<>(
                Objects.requireNonNull(signals, "signals")
                    .stream()
                    .sorted(Comparator.comparing(SignalName::value))
                    .toList()
            )
        );
        if (signals.isEmpty()) {
            throw new IllegalArgumentException("A binding must name at least one signal");
        }
        ArtifactKind kind = signals.getFirst().artifactKind();
        for (SignalName signal : signals) {
            if (!kind.equals(signal.artifactKind())) {
                throw new IllegalArgumentException(
                    "One binding cannot mix artifact kinds: " + kind + " and " + signal.artifactKind()
                );
            }
        }
        needs = Objects.requireNonNull(needs, "needs")
            .stream()
            .sorted(Comparator.comparing(need -> need.sourceKind().value()))
            .toList();
        Set<String> seen = new HashSet<>();
        for (PracticeEvidenceRequirement need : needs) {
            if (!seen.add(need.sourceKind().value())) {
                throw new IllegalArgumentException("needs contains duplicate source " + need.sourceKind());
            }
        }
    }

    /** A binding on one signal that reads the evidence the artifact kind's default names. */
    public static PracticeBinding on(SignalName signal, List<PracticeEvidenceRequirement> needs) {
        return new PracticeBinding(List.of(signal), needs, false);
    }

    /**
     * The kind of artifact this binding is about, read off its signals' shared prefix.
     *
     * <p>Never serialized: it is already spelled out in every signal name, and writing it a second time
     * is what allowed a practice's declared kind and its triggers to disagree.
     */
    @JsonIgnore
    public ArtifactKind artifactKind() {
        return signals.getFirst().artifactKind();
    }

    /** Whether this signal occasions this review. */
    public boolean matches(SignalName signal) {
        return signals.contains(signal);
    }

    /**
     * Whether an artifact in the given draft state occasions this review.
     *
     * <p>A non-draft artifact always does; a draft one only where the author said so. The question is
     * asked per binding because it is a property of the occasion: judging how a change was handed over
     * is exactly what one wants to say about a draft, while judging how a merged change was reviewed
     * cannot arise on one.
     */
    public boolean occasionedBy(SignalName signal, boolean draft) {
        return matches(signal) && (!draft || onDrafts);
    }

    /**
     * The evidence a review of these practices occasioned by {@code signal} reads.
     *
     * <p>A {@code null} signal means nobody named an occasion — an explicit ask, or a replay that
     * predates the ledger. Every binding then contributes, because a review somebody asked for is a
     * request to apply the practice as widely as it can apply, and narrowing it silently to one
     * binding's evidence would answer a different question than the one asked.
     */
    public static List<PracticeEvidenceRequirement> needsFor(
        List<PracticeBinding> bindings,
        @Nullable SignalName signal
    ) {
        Set<PracticeEvidenceRequirement> union = new LinkedHashSet<>();
        for (PracticeBinding binding : bindings) {
            if (signal == null || binding.matches(signal)) {
                union.addAll(binding.needs());
            }
        }
        return union.stream().sorted(Comparator.comparing(need -> need.sourceKind().value())).toList();
    }

    /** The one artifact kind every binding in the list is about. */
    public static ArtifactKind artifactKindOf(List<PracticeBinding> bindings) {
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("A practice must declare at least one binding");
        }
        ArtifactKind kind = bindings.getFirst().artifactKind();
        for (PracticeBinding binding : bindings) {
            if (!kind.equals(binding.artifactKind())) {
                throw new IllegalArgumentException(
                    "A practice reviews one kind of artifact; bindings name " + kind + " and " + binding.artifactKind()
                );
            }
        }
        return kind;
    }

    /** Every signal named by any binding, in order. */
    public static List<SignalName> signalsOf(List<PracticeBinding> bindings) {
        return bindings
            .stream()
            .flatMap(binding -> binding.signals().stream())
            .distinct()
            .toList();
    }
}
