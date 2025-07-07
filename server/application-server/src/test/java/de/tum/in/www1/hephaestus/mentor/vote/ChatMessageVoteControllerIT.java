package de.tum.in.www1.hephaestus.mentor.vote;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.mentor.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ChatMessageVoteController.
 */
@AutoConfigureWebTestClient
public class ChatMessageVoteControllerIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ChatMessageVoteRepository voteRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatThreadRepository threadRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = userRepository.findByLogin("mentor").orElseThrow();
    }

    @Test
    @WithMentorUser
    void voteMessage_UpvoteAssistantMessage_CreatesVoteSuccessfully() {
        // Arrange: Create test message
        UUID messageId = createTestAssistantMessage();
        var request = new VoteMessageRequestDTO(true);

        // Act: Vote on the message 
        var result = webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", messageId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageVoteDTO.class)
                .returnResult()
                .getResponseBody();

        // Assert: Response is correct
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(messageId);
        assertThat(result.isUpvoted()).isTrue();

        // Assert: Vote was persisted correctly
        var savedVote = voteRepository.findById(messageId);
        assertThat(savedVote).isPresent();
        assertThat(savedVote.get().getIsUpvoted()).isTrue();
    }

    @Test
    @WithMentorUser
    void voteMessage_DownvoteAssistantMessage_CreatesVoteSuccessfully() {
        // Arrange: Create test message
        UUID messageId = createTestAssistantMessage();
        var request = new VoteMessageRequestDTO(false);

        // Act: Vote on the message
        var result = webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", messageId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageVoteDTO.class)
                .returnResult()
                .getResponseBody();

        // Assert: Response is correct
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(messageId);
        assertThat(result.isUpvoted()).isFalse();

        // Assert: Vote was persisted correctly
        var savedVote = voteRepository.findById(messageId);
        assertThat(savedVote).isPresent();
        assertThat(savedVote.get().getIsUpvoted()).isFalse();
    }

    @Test
    @WithMentorUser
    void voteMessage_DownvoteAssistantMessage_CreatesDownvoteSuccessfully() {
        // Arrange: Create test message
        UUID messageId = createTestAssistantMessage();
        var request = new VoteMessageRequestDTO(false); // Downvote

        // Act: Vote on the message 
        var result = webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", messageId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageVoteDTO.class)
                .returnResult()
                .getResponseBody();

        // Assert: Response is correct
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(messageId);
        assertThat(result.isUpvoted()).isFalse(); // Downvote

        // Assert: Vote was persisted correctly
        var savedVote = voteRepository.findById(messageId);
        assertThat(savedVote).isPresent();
        assertThat(savedVote.get().getIsUpvoted()).isFalse();
    }

    @Test
    @WithMentorUser
    void voteMessage_UpdateExistingVote_UpdatesVoteSuccessfully() {
        // Arrange: Create message with existing upvote
        UUID messageId = createTestAssistantMessage();
        var existingVote = new ChatMessageVote(messageId, true);
        voteRepository.save(existingVote);
        var request = new VoteMessageRequestDTO(false);

        // Act: Change vote to downvote
        var result = webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", messageId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageVoteDTO.class)
                .returnResult()
                .getResponseBody();

        // Assert: Response is correct
        assertThat(result).isNotNull();
        assertThat(result.isUpvoted()).isFalse();

        // Assert: Vote was updated, not duplicated
        var votes = voteRepository.findAll();
        var ourVotes = votes.stream()
                .filter(v -> v.getMessageId().equals(messageId))
                .toList();
        assertThat(ourVotes).hasSize(1);
        assertThat(ourVotes.get(0).getIsUpvoted()).isFalse();
    }

    @Test
    @WithMentorUser
    void voteMessage_MessageNotFound_ReturnsNotFound() {
        // Arrange: Non-existent message ID
        var nonExistentId = UUID.randomUUID();
        var request = new VoteMessageRequestDTO(true);

        // Act & Assert: Non-existent message returns 404
        webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", nonExistentId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMentorUser
    void voteMessage_NonExistentMessage_Returns404() {
        // Arrange: Use a random UUID that doesn't exist
        UUID nonExistentMessageId = UUID.randomUUID();
        var request = new VoteMessageRequestDTO(true);

        // Act & Assert: Voting on non-existent message should return 404
        webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", nonExistentMessageId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMentorUser
    void voteMessage_NullVote_ReturnsBadRequest() {
        // Arrange: Create test message
        UUID messageId = createTestAssistantMessage();
        var request = new VoteMessageRequestDTO(null);

        // Act & Assert: Null vote returns 400
        webTestClient.patch()
                .uri("/api/chat/messages/{messageId}/vote", messageId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        // Assert: No vote was created
        assertThat(voteRepository.findById(messageId)).isEmpty();
    }

    // ==================== Helper Methods ====================

    private UUID createTestAssistantMessage() {
        // Create a minimal thread and message for testing
        var thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setCreatedAt(Instant.now());
        thread.setTitle("Test Thread");
        thread.setUser(testUser); // Properly set the user
        var savedThread = threadRepository.save(thread);

        var message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setThread(savedThread);
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setCreatedAt(Instant.now());

        // Create the required ChatMessagePart
        var part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(message.getId(), 0));
        part.setMessage(message);
        part.setType(ChatMessagePart.PartType.TEXT);
        part.setContent(objectMapper.createObjectNode().put("text", "Test assistant response"));
        
        message.setParts(List.of(part));
        var savedMessage = messageRepository.save(message);
        
        return savedMessage.getId();
    }
}