package de.tum.cit.aet.hephaestus.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;
import java.util.Set;

/** Evaluation-only transformation applied after an immutable base capture. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(@JsonSubTypes.Type(value = EvidenceViewTransformation.Ablation.class, name = "ABLATION"))
public sealed interface EvidenceViewTransformation permits EvidenceViewTransformation.Ablation {
    record Ablation(String planId, Set<SourceKind> removedSources) implements EvidenceViewTransformation {
        public Ablation {
            Objects.requireNonNull(planId, "planId");
            if (planId.isBlank()) {
                throw new IllegalArgumentException("planId must not be blank");
            }
            removedSources = Set.copyOf(Objects.requireNonNull(removedSources, "removedSources"));
            if (removedSources.isEmpty()) {
                throw new IllegalArgumentException("An ablation must remove at least one source");
            }
        }
    }
}
