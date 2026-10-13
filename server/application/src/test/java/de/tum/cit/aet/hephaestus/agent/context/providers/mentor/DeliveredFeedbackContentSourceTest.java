package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceFolderPersonDataCatalog;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository.FeedbackResultVisibility;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.RecipientFeedbackRow;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackWithdrawalRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class DeliveredFeedbackContentSourceTest extends BaseUnitTest {

    private static final Instant AT = Instant.parse("2026-06-17T08:30:00Z");

    @Mock
    UserRepository userRepository;

    @Mock
    FeedbackRepository feedbackRepository;

    @Mock
    FeedbackEvidenceRepository feedbackEvidenceRepository;

    @Mock
    FeedbackWithdrawalRepository withdrawalRepository;

    @Mock
    ConversationConsentGate conversationConsentGate;

    @Mock
    ResultVisibilityPolicy visibilityPolicy;

    @Mock
    EvidenceFolderPersonDataCatalog personCopies;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    DeliveredFeedbackContentSource provider;

    @BeforeEach
    void vouchFixtureJobs() {
        lenient()
                .when(personCopies.recordVouchedHistoricalJobs(anyLong(), any()))
                .thenAnswer(invocation -> Set.copyOf(invocation.<Collection<UUID>>getArgument(1)));
    }

    @Test
    void shouldPreserveDeliveryFactsButOmitWordsWithoutSourceAuthority() throws Exception {
        givenUser();
        Row unsafe = delivered(ArtifactKinds.PULL_REQUEST, 575L, "Earlier third-person words");
        Row trusted = delivered(ArtifactKinds.PULL_REQUEST, 576L, "Trusted guidance");
        givenRows(unsafe, trusted);
        authorize(unsafe, trusted);
        doReturn(Set.of(trusted.getAgentJobId())).when(personCopies).recordVouchedHistoricalJobs(eq(1L), any());

        JsonNode root = contribute();

        assertThat(root.path("deliveredFeedback")).hasSize(1);
        assertThat(root.path("deliveredFeedback").get(0).path("body").asString())
                .isEqualTo("Trusted guidance");
        assertThat(root.path("feedbackStates")).hasSize(2);
        JsonNode state = root.path("feedbackStates").get(0);
        assertThat(state.path("feedbackId").asString()).isEqualTo(unsafe.getId().toString());
        assertThat(state.path("status").asString()).isEqualTo("DELIVERED");
        assertThat(state.path("wordsUnavailable").asString()).isEqualTo("COPY_AUTHORITY_UNAVAILABLE");
        assertThat(state.has("withdrawn")).isFalse();
        assertThat(root.path("wordsCoverage").asString()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("no feedback → empty arrays")
    void emptyDefaults() throws Exception {
        givenUser();
        givenRows();

        JsonNode root = contribute();

        assertThat(root.get("user").get("login").asString()).isEqualTo("octo");
        assertThat(root.get("deliveredFeedback")).isEmpty();
        assertThat(root.get("feedbackStates")).isEmpty();
    }

    @Test
    void shouldNameWhatTheSampleCannotShowWhenItIsEmpty() throws Exception {
        givenUser();
        givenRows();
        Instant before = Instant.now();

        JsonNode root = contribute();

        JsonNode coverage = root.get("coverage");
        assertThat(coverage.propertyNames())
                .containsExactlyInAnyOrder("scope", "preparedAt", "lookbackDays", "maxEntries", "outsideScope");
        assertThat(coverage.get("scope").asString()).isEqualTo("CONVERSATION_AUTHORIZED_RECIPIENT_RECORDS");
        assertThat(Instant.parse(coverage.get("preparedAt").asString())).isBetween(before, Instant.now());
        assertThat(coverage.get("lookbackDays").asInt()).isEqualTo(90);
        assertThat(coverage.get("maxEntries").asInt()).isEqualTo(30);
        assertThat(coverage.get("outsideScope"))
                .extracting(JsonNode::asString)
                .containsExactly(
                        "PROPOSALS",
                        "REVIEWER_DECISIONS",
                        "WITHHELD",
                        "REPLACED",
                        "EVIDENCE_NOT_USABLE_IN_CONVERSATION",
                        "CONVERSATION_CONSENT_NOT_ACTIVE");
        assertThat(root.has("lookbackDays")).isFalse();
    }

    @Test
    @DisplayName("ships the delivered body; a blank one keeps its status but no text, and null artifacts are omitted")
    void shipsRenderedBodyAndSkipsBlank() throws Exception {
        givenUser();
        Row withBody = delivered(ArtifactKinds.PULL_REQUEST, 575L, "Nice work scoping this PR.");
        Row blank = delivered(null, null, "   ");
        givenRows(withBody, blank);
        authorize(withBody, blank);

        JsonNode root = contribute();

        assertThat(root.get("deliveredFeedback")).hasSize(1);
        JsonNode only = root.get("deliveredFeedback").get(0);
        assertThat(only.get("feedbackId").asString()).isEqualTo(withBody.getId().toString());
        assertThat(only.get("surface").asString()).isEqualTo("IN_CONTEXT");
        assertThat(only.get("artifactKind").asString()).isEqualTo("scm.pull_request");
        assertThat(only.get("artifactId").asLong()).isEqualTo(575L);
        assertThat(only.get("body").asString()).isEqualTo("Nice work scoping this PR.");
        JsonNode blankState = root.get("feedbackStates").get(1);
        assertThat(blankState.get("status").asString()).isEqualTo("DELIVERED");
        assertThat(blankState.has("artifactKind")).isFalse();
        assertThat(blankState.has("artifactId")).isFalse();
    }

    @Test
    @DisplayName("source authorization is re-evaluated for every turn")
    void authorizationIsReevaluatedForEveryTurn() throws Exception {
        givenUser();
        givenRows();

        contribute();
        contribute();

        verify(feedbackRepository, times(2))
                .findRecentReceivableForRecipient(
                        eq(1L), eq(2L), any(Instant.class), any(), any(), any(Pageable.class));
    }

    @Test
    void keepsAConversationStatusButNeverItsBody() throws Exception {
        givenUser();
        Row chat = new Row(
                UUID.randomUUID(),
                UUID.randomUUID(),
                FeedbackChannel.IN_CHAT,
                FeedbackDeliveryState.DELIVERED,
                null,
                null,
                AT,
                AT,
                "composer notes for the mentor");
        givenRows(chat);
        authorize(chat);

        JsonNode root = contribute();

        assertThat(root.get("deliveredFeedback")).isEmpty();
        assertThat(root.get("feedbackStates").get(0).get("status").asString()).isEqualTo("DELIVERED");
        assertThat(root.toString()).doesNotContain("composer notes for the mentor");
    }

    @Test
    void withholdsFeedbackWhenItsResultIsNoLongerVisible() {
        givenUser();
        Row row = delivered(ArtifactKinds.PULL_REQUEST, 575L, "Previously delivered");
        givenRows(row);
        FeedbackResultVisibility binding = binding(row);
        when(feedbackEvidenceRepository.findForVisibility(eq(1L), any())).thenReturn(List.of(binding));
        when(visibilityPolicy.permitsShown(eq(1L), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING)))
                .thenReturn(Set.of());

        ObjectNode root = provider.buildPayload(1L, 2L);

        assertThat(root.path("deliveredFeedback")).isEmpty();
        assertThat(root.path("feedbackStates")).isEmpty();
    }

    @Test
    void readsAFixedNumberOfPagesWhenEveryRowIsHidden() {
        givenUser();
        Row beyondBudget = delivered(ArtifactKinds.PULL_REQUEST, 999L, "beyond-the-budget");
        AtomicInteger pages = new AtomicInteger();
        when(feedbackRepository.findRecentReceivableForRecipient(
                        eq(1L), eq(2L), any(Instant.class), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> pages.getAndIncrement() < 5
                        ? Stream.generate(() -> delivered(ArtifactKinds.PULL_REQUEST, 1L, "hidden-body"))
                                .limit(30)
                                .toList()
                        : List.of(beyondBudget));
        authorize(beyondBudget);

        ObjectNode root = provider.buildPayload(1L, 2L);

        verify(feedbackRepository, times(5))
                .findRecentReceivableForRecipient(
                        eq(1L), eq(2L), any(Instant.class), any(), any(), any(Pageable.class));
        assertThat(root.path("feedbackStates")).isEmpty();
        assertThat(root.toString()).doesNotContain("hidden-body", "beyond-the-budget");
    }

    private JsonNode contribute() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);
        return objectMapper.readTree(files.get("inputs/context/delivered_feedback.json"));
    }

    private void givenUser() {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
    }

    private void givenRows(RecipientFeedbackRow... rows) {
        when(feedbackRepository.findRecentReceivableForRecipient(
                        eq(1L), eq(2L), any(Instant.class), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(rows));
    }

    private void authorize(Row... rows) {
        List<FeedbackResultVisibility> bindings = new ArrayList<>();
        Set<UUID> permitted = new HashSet<>();
        for (Row row : rows) {
            FeedbackResultVisibility binding = binding(row);
            bindings.add(binding);
            permitted.add(binding.getPracticeResult().getId());
        }
        when(feedbackEvidenceRepository.findForVisibility(eq(1L), any())).thenReturn(bindings);
        when(visibilityPolicy.permitsShown(eq(1L), any(), eq(SourceUsePurpose.CONVERSATIONAL_MENTORING)))
                .thenReturn(permitted);
    }

    private static FeedbackResultVisibility binding(Row row) {
        PracticeResult practiceResult = mock(PracticeResult.class);
        when(practiceResult.getId()).thenReturn(UUID.randomUUID());
        when(practiceResult.getAgentJobId()).thenReturn(row.getAgentJobId());
        when(practiceResult.getPractice()).thenReturn(new Practice());
        FeedbackResultVisibility binding = mock(FeedbackResultVisibility.class);
        when(binding.getFeedbackId()).thenReturn(row.getId());
        when(binding.getPracticeResult()).thenReturn(practiceResult);
        return binding;
    }

    private static Row delivered(@Nullable ArtifactKind artifactKind, @Nullable Long artifactId, String body) {
        return new Row(
                UUID.randomUUID(),
                UUID.randomUUID(),
                FeedbackChannel.IN_CONTEXT,
                FeedbackDeliveryState.DELIVERED,
                artifactKind,
                artifactId,
                AT,
                AT,
                body);
    }

    private record Row(
            UUID getId,
            UUID getAgentJobId,
            FeedbackChannel getChannel,
            FeedbackDeliveryState getDeliveryState,
            @Nullable ArtifactKind getArtifactKind,
            @Nullable Long getArtifactId,
            Instant getCreatedAt,
            @Nullable Instant getDeliveredAt,
            @Nullable String getBody)
            implements RecipientFeedbackRow {}
}
