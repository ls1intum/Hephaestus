package de.tum.in.www1.hephaestus.mentor.vote;import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;import io.swagger.v3.oas.annotations.Operation;import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for chat message voting.
 */
@RestController
@RequestMapping("/api/chat/messages")
@Tag(name = "Chat Message Voting", description = "Vote on chat messages (upvote/downvote)")
public class ChatMessageVoteController {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageVoteController.class);

    private final ChatMessageVoteService voteService;
    private final ChatMessageRepository messageRepository;

    public ChatMessageVoteController(
            ChatMessageVoteService voteService,
            ChatMessageRepository messageRepository) {
        this.voteService = voteService;
        this.messageRepository = messageRepository;
    }

    @Operation(summary = "Vote on a message", description = "Cast an upvote or downvote on a chat message")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vote successfully recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid vote type or message not found"),
        @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @PatchMapping("/{messageId}/vote")
    public ResponseEntity<ChatMessageVoteDTO> voteMessage(
            @Parameter(description = "Message ID to vote on") @PathVariable UUID messageId,
            @Valid @RequestBody VoteMessageRequestDTO request) {

        logger.debug("Vote request for message: {} with vote: {}", messageId, request.isUpvoted());

        // Check if message exists
        var messageOptional = messageRepository.findById(messageId);
        if (messageOptional.isEmpty()) {
            logger.warn("Message not found: {}", messageId);
            return ResponseEntity.notFound().build();
        }

        try {
            ChatMessageVoteDTO vote = voteService.voteMessage(messageId, request.isUpvoted());
            logger.debug("Vote successful: {}", vote);
            return ResponseEntity.ok(vote);
        } catch (Exception e) {
            logger.error("Error voting on message {}: {}", messageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}