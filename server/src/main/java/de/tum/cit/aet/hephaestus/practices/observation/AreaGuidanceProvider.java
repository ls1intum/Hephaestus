package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Supplies the developer-facing guidance text shown on a practice-area status card.
 *
 * <p>The card's centre text is a seam, not a fixed rule: without a provider bean,
 * {@link PracticeAreaStatusService} composes a deterministic sentence from the area's practice standings.
 * Registering a bean that implements this interface swaps in richer aggregated guidance — e.g. a
 * nightly LLM summary over all of the area's feedback, persisted per user × area — without touching
 * the endpoint, the DTO, or the card. Areas missing from the returned map keep the deterministic
 * fallback, so a provider may cover only the areas it has fresh material for.
 *
 * <p>Batch-shaped on purpose: the profile renders every active area in one request, so a
 * snapshot-backed implementation can answer with a single query instead of one per card.
 */
public interface AreaGuidanceProvider {
    /**
     * Aggregated guidance for the given developer's areas, keyed by area slug. Slugs absent from the
     * map fall back to the rule-based sentence; never map a slug to {@code null}.
     */
    @NonNull
    Map<String, AreaGuidance> findGuidance(
        @NonNull Long workspaceId,
        @NonNull Long userId,
        @NonNull Collection<String> areaSlugs
    );

    /** One area's guidance text plus its provenance for API consumers and later refresh policies. */
    record AreaGuidance(@NonNull String text, PracticeAreaStatusDTO.@NonNull GuidanceSource source) {}
}
