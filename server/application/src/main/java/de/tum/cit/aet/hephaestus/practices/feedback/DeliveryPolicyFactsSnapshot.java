package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSubjectStatus;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewPersonMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryMode;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record DeliveryPolicyFactsSnapshot(
        @Nullable String artifactKind,
        @Nullable String repository,
        @Nullable String baseBranch,
        @Nullable ReviewSubjectStatus subject,
        @Nullable ReviewRepositoryMode repositoryMode,
        @Nullable ReviewPersonMode personMode,
        @Nullable Boolean repositoryMatched,
        @Nullable Boolean branchMatched,
        @Nullable Boolean personMatched,
        @Nullable Boolean recipientConsent,
        @Nullable PracticeDeliveryStatus deliveryStatus,
        @Nullable TriggerMode triggerMode,
        List<PracticeFact> contributingPractices) {
    public DeliveryPolicyFactsSnapshot {
        contributingPractices = contributingPractices == null ? List.of() : List.copyOf(contributingPractices);
    }

    public record PracticeFact(String slug, PracticeAutonomy autonomy) {}
}
