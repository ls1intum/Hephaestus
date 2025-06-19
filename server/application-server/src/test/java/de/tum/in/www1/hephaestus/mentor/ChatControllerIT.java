package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@AutoConfigureWebTestClient
public class ChatControllerIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MockChatResponseHolder mockResponseHolder;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void testUnauthenticatedRequestShouldBeRejected() {
        // Given
        var request = createChatRequest();

        // When & Then - unauthenticated request should be rejected
        webTestClient
            .post()
            .uri("/mentor/chat")
            .headers(TestAuthUtils.withCurrentUserOrNone()) // No auth header
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isUnauthorized(); // Expect 401 Unauthorized
    }

    @Test
    @WithMentorUser
    void testShouldPersistTextMessageStream() {
        // Given
        var request = createChatRequest();
        String responseMessageId = UUID.randomUUID().toString();
        mockResponseHolder.setStreamParts(
            request.messages().getLast().getId(),
            List.of(
                new StreamStartPart().messageId(responseMessageId),
                new StreamStepStartPart(),
                new StreamTextPart().text("Hello,"),
                new StreamTextPart().text(" this"),
                new StreamTextPart().text(" is"),
                new StreamTextPart().text(" a"),
                new StreamTextPart().text(" test!"),
                new StreamStepFinishPart(),
                new StreamFinishPart()
            )
        );

        // When
        var response = performChatRequest(request);

        // Then
        StepVerifier.create(response).expectComplete();

        // Thread should have been created with the request ID
        ChatThread thread = chatThreadRepository
            .findById(UUID.fromString(request.id()))
            .orElseThrow(() -> new AssertionError("No chat thread found"));
        assertThat(thread.getUser().getLogin()).isEqualTo("mentor");
        assertThat(thread.getTitle()).isEqualTo("New chat");

        // User message should have been created with the request ID
        ChatMessage userMessage = chatMessageRepository
            .findById(UUID.fromString(request.messages().getLast().getId()))
            .orElseThrow(() -> new AssertionError("No user message from request found"));
        assertThat(userMessage.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMessage.getParts()).hasSize(1); // Only one part for the user message

        ChatMessagePart userPart = userMessage.getParts().get(0);
        assertThat(userPart.getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(userPart.getContent().asText()).isEqualTo("Hello, World!");

        // Assistant message should have been created with the response ID
        ChatMessage assistantMessage = chatMessageRepository
            .findById(UUID.fromString(responseMessageId))
            .orElseThrow(() -> new AssertionError("No assistant message found"));
        assertThat(assistantMessage.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(assistantMessage.getParts()).hasSize(1); // Text chunks should be combined into one part
        ChatMessagePart assistantPart = assistantMessage.getParts().get(0);
        assertThat(assistantPart.getType()).isEqualTo(ChatMessagePart.PartType.TEXT);
        assertThat(assistantPart.getContent().asText()).isEqualTo("Hello, this is a test!");

        // Relationships should be established
        assertThat(assistantMessage.getThread()).isEqualTo(thread);
        assertThat(userMessage.getThread()).isEqualTo(thread);
        assertThat(userMessage.getParentMessage()).isNull(); // Root message has no parent
        assertThat(assistantMessage.getParentMessage()).isEqualTo(userMessage);
        assertThat(thread.getAllMessages()).containsExactly(userMessage, assistantMessage);
        assertThat(thread.getSelectedLeafMessage()).isEqualTo(assistantMessage);
    }

    private ChatRequestDTO createChatRequest() {
        UUID requestThreadId = UUID.randomUUID();
        UUID requestMessageId = UUID.randomUUID();

        var message = new UIMessage();
        message.setId(requestMessageId.toString());
        message.setRole(UIMessage.RoleEnum.USER);
        message.addPartsItem(new UIMessagePartsInner().type("text").text("Hello, World!"));

        return new ChatRequestDTO(requestThreadId.toString(), List.of(message));
    }

    private Flux<String> performChatRequest(ChatRequestDTO request) {
        return webTestClient
            .post()
            .uri("/mentor/chat")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(String.class)
            .getResponseBody();
    }
}
