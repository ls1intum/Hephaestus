package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.MemberAiRoutingAdapter;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewModelBindings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes the complete method slots; credentials and prices stay in runtime admission. */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewModelBindingsAdapter implements ReviewModelBindings {
    private final MemberAiRoutingAdapter routing;
    private final ReviewMethodVerifier methods;

    @Override
    @Transactional(readOnly = true)
    public List<BoundModelSlot> models(long workspaceId, List<@Nullable Long> developerIds) {
        if (developerIds.isEmpty())
            return List.of(
                    new BoundModelSlot("chat", ModelKind.CHAT, null),
                    new BoundModelSlot("decision", ModelKind.DECISION, null),
                    new BoundModelSlot("embedding", ModelKind.EMBEDDING, null),
                    new BoundModelSlot("reranking", ModelKind.RERANKING, null),
                    new BoundModelSlot("review", ModelKind.CHAT, null));
        var review = routing.bindingForPeople(workspaceId, AgentPurpose.PRACTICE_REVIEW, developerIds);
        var slots = new ArrayList<BoundModelSlot>();
        var primary = review.map(row -> methods.freeze(row)).orElse(null);
        slots.add(new BoundModelSlot("review", ModelKind.CHAT, primary));
        slots.add(new BoundModelSlot("chat", ModelKind.CHAT, primary));
        for (var purpose : AgentPurpose.practiceScript()) {
            slots.add(new BoundModelSlot(
                    purpose.kind().name().toLowerCase(Locale.ROOT),
                    purpose.kind(),
                    routing.bindingForPeople(workspaceId, purpose, developerIds)
                            .map(row -> methods.freeze(row))
                            .orElse(null)));
        }
        return slots.stream().sorted(Comparator.comparing(BoundModelSlot::slot)).toList();
    }
}
