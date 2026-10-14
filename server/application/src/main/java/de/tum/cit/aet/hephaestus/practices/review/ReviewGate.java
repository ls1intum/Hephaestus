package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewGate {
    private final PracticeRepository practices;
    private final JsonMapper json;
    private final PracticeReviewReadiness readiness;

    @Transactional(readOnly = true)
    public GateDecision evaluate(
            Workspace workspace,
            ItemSnapshot item,
            ReviewOrigin origin,
            @Nullable ItemEventName event,
            Collection<Long> selected) {
        if (!Objects.equals(workspace.getId(), item.item().workspaceId()))
            throw new IllegalArgumentException("The item belongs to another workspace.");
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE)
            return new GateDecision.Skip("The workspace is inactive.", ItemEventReason.WORKSPACE_INACTIVE);
        if (!readiness.hasRunnableAgent(workspace.getId()))
            return new GateDecision.Skip("The review model is unavailable.", ItemEventReason.REVIEW_MODEL_UNBOUND);
        var matched = practices
                .findByWorkspaceIdAndKind(workspace.getId(), item.item().kind())
                .stream()
                .filter(p -> selected.isEmpty() || selected.contains(p.getId()))
                .filter(p -> runnable(p, item.state(), origin, event))
                .toList();
        if (matched.isEmpty())
            return new GateDecision.Skip("No practice can review this item.", ItemEventReason.NO_ACTIVE_PRACTICE);
        return new GateDecision.Run(
                workspace, matched, workspace.getReviewSettings().getRolloutRevision(), origin);
    }

    private boolean runnable(
            Practice practice, Map<String, String> state, ReviewOrigin origin, @Nullable ItemEventName event) {
        var definition = json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class);
        if (definition.reviewedBy().by() != ReviewedByKind.HEPHAESTUS) return false;
        if (origin != ReviewOrigin.EVENT) return true;
        return event != null
                && definition.reviewOn().events().contains(event)
                && definition.reviewOn().state().entrySet().stream()
                        .allMatch(e ->
                                state.containsKey(e.getKey()) && e.getValue().contains(state.get(e.getKey())));
    }
}
