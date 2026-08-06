package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.FeedbackObservationVisibility;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class DeliveredFeedbackContentSource implements ContentSource {

    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "delivered_feedback.json";

    private static final int LOOKBACK_DAYS = 90;
    private static final int MAX_DELIVERED = 30;

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ConversationConsentGate conversationConsentGate;
    private final ObservationVisibilityPolicy visibilityPolicy;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof MentorChatRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        MentorChatRequest req = (MentorChatRequest) request;
        ObjectNode payload = buildPayload(req.workspaceId(), req.developerId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize delivered feedback context", e);
        }
    }

    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        User user = userRepository
            .findById(developerId)
            .orElseThrow(() -> new EntityNotFoundException("User", developerId.toString()));
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<Feedback> candidates = feedbackRepository.findRecentDeliveredForRecipient(
            workspaceId,
            developerId,
            since,
            PageRequest.of(0, MAX_DELIVERED)
        );

        List<UUID> feedbackIds = candidates.stream().map(Feedback::getId).filter(java.util.Objects::nonNull).toList();
        List<FeedbackObservationVisibility> bindings = feedbackIds.isEmpty()
            ? List.of()
            : feedbackObservationRepository.findForVisibility(workspaceId, feedbackIds);
        Map<UUID, List<Observation>> observationsByFeedback = bindings
            .stream()
            .collect(
                Collectors.groupingBy(
                    FeedbackObservationVisibility::getFeedbackId,
                    Collectors.mapping(FeedbackObservationVisibility::getObservation, Collectors.toList())
                )
            );
        List<Feedback> delivered = candidates
            .stream()
            .filter(feedback -> {
                List<Observation> observations = observationsByFeedback.getOrDefault(feedback.getId(), List.of());
                return (
                    !observations.isEmpty() &&
                    observations
                        .stream()
                        .allMatch(observation ->
                            visibilityPolicy.permits(
                                workspaceId,
                                observation,
                                SourceUsePurpose.CONVERSATIONAL_MENTORING
                            )
                        )
                );
            })
            .toList();

        Set<Long> activeThreadIds = conversationConsentGate.activeThreadIds(
            workspaceId,
            conversationThreadIds(delivered)
        );
        boolean anyConversationSurvivor = delivered.stream().anyMatch(f -> isSurvivingConversation(f, activeThreadIds));

        ObjectNode root = objectMapper.createObjectNode();
        // Untrusted-content quarantine: only when a Slack-derived (attacker-controllable) body survives the gate does
        // this file carry the envelope — a PR/issue-only payload stays byte-identical (no _meta).
        if (anyConversationSurvivor) {
            conversationConsentGate.writeUntrustedEnvelope(root);
        }
        root.putObject("user").put("login", user.getLogin()).put("name", user.getName());
        root.put("lookbackDays", LOOKBACK_DAYS);

        ArrayNode arr = root.putArray("deliveredFeedback");
        for (Feedback f : delivered) {
            String body = f.getBody();
            if (body == null || body.isBlank()) {
                continue;
            }
            if (
                ArtifactKinds.CONVERSATION_THREAD.equals(f.getArtifactKind()) &&
                !isSurvivingConversation(f, activeThreadIds)
            ) {
                continue;
            }
            ObjectNode node = arr.addObject();
            node.put("surface", f.getChannel().name());
            if (f.getArtifactKind() != null) {
                node.put("artifactKind", f.getArtifactKind().value());
            }
            if (f.getArtifactId() != null) {
                node.put("artifactId", f.getArtifactId());
            }
            if (f.getDeliveredAt() != null) {
                node.put("deliveredAt", f.getDeliveredAt().toString());
            }
            node.put("body", body);
        }
        root.put("totalDelivered", arr.size());
        return root;
    }

    private static List<Long> conversationThreadIds(List<Feedback> units) {
        List<Long> ids = new ArrayList<>();
        for (Feedback f : units) {
            if (ArtifactKinds.CONVERSATION_THREAD.equals(f.getArtifactKind()) && f.getArtifactId() != null) {
                ids.add(f.getArtifactId());
            }
        }
        return ids;
    }

    private static boolean isSurvivingConversation(Feedback f, Set<Long> activeThreadIds) {
        return (
            ArtifactKinds.CONVERSATION_THREAD.equals(f.getArtifactKind()) &&
            f.getArtifactId() != null &&
            activeThreadIds.contains(f.getArtifactId())
        );
    }
}
