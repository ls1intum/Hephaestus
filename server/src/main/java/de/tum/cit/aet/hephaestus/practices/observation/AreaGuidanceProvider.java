package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Supplies the guidance text on a practice-area status card.
 *
 * <p>A seam, not a fixed rule: with no bean registered, {@link PracticeAreaStatusService} composes a
 * deterministic sentence from the practice standings. A bean swaps in richer guidance, such as a nightly LLM
 * summary, without touching the endpoint, the DTO or the card.
 *
 * <p>Batch-shaped because the profile renders every card in one request. A slug absent from the returned map
 * keeps the deterministic fallback, so a provider may cover only what it has fresh material for.
 */
public interface AreaGuidanceProvider {
    /** Guidance keyed by slug. An absent slug falls back to the rule-based sentence; never map one to null. */
    @NonNull
    Map<String, AreaGuidance> findGuidance(
        @NonNull Long workspaceId,
        @NonNull Long userId,
        @NonNull Collection<String> areaSlugs
    );

    /** One area's guidance text plus its provenance for API consumers and later refresh policies. */
    record AreaGuidance(@NonNull String text, PracticeAreaStatusDTO.@NonNull GuidanceSource source) {}
}
