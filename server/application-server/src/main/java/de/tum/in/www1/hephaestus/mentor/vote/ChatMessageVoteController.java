package de.tum.in.www1.hephaestus.mentor.vote;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for chat message voting.
 */
@WorkspaceScopedController
@RequestMapping("/api/chat/messages")
@Tag(name = "Chat Message Voting", description = "Vote on chat messages (upvote/downvote)")
public class ChatMessageVoteController {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageVoteController.class);

    private final ChatMessageVoteService voteService;
    private final ChatMessageRepository messageRepository;
    private final WorkspaceContextResolver workspaceResolver;

    public ChatMessageVoteController(
        ChatMessageVoteService voteService,
        ChatMessageRepository messageRepository,
        WorkspaceContextResolver workspaceResolver
    ) {
        this.voteService = voteService;
        this.messageRepository = messageRepository;
        this.workspaceResolver = workspaceResolver;
    }

    @Operation(summary = "Vote on a message", description = "Cast an upvote or downvote on a chat message")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Vote successfully recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid vote type or message not found"),
            @ApiResponse(responseCode = "404", description = "Message not found"),
        }
    )
    @PatchMapping("/{messageId}/vote")
    public ResponseEntity<ChatMessageVoteDTO> voteMessage(
        WorkspaceContext workspaceContext,
        @Parameter(description = "Message ID to vote on") @PathVariable UUID messageId,
        @Valid @RequestBody VoteMessageRequestDTO request
    ) {
        logger.debug(
            "Vote request for message: {} with vote: {} in workspace {}",
            messageId,
            request.isUpvoted(),
            workspaceContext.slug()
        );

        // Check if message exists
        var messageOptional = messageRepository.findById(messageId);
        if (messageOptional.isEmpty()) {
            logger.warn("Message not found: {}", messageId);
            return ResponseEntity.notFound().build();
        }

        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        var message = messageOptional.get();
        if (
            message.getThread() == null ||
            message.getThread().getWorkspace() == null ||
            !workspace.getId().equals(message.getThread().getWorkspace().getId())
        ) {
            logger.warn("Message {} does not belong to workspace {}", messageId, workspaceContext.slug());
            return ResponseEntity.notFound().build();
        }

        // Authorization: only the owner of the thread may vote on their messages
        try {
            var currentUserLogin = SecurityUtils.getCurrentUserLoginOrThrow();
            var ownerLogin = message.getThread() != null && message.getThread().getUser() != null
                ? message.getThread().getUser().getLogin().toLowerCase()
                : null;
            if (ownerLogin == null || !ownerLogin.equals(currentUserLogin)) {
                logger.warn("User {} attempted to vote on message {} not owned by them", currentUserLogin, messageId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (Exception e) {
            logger.error("Failed to resolve current user during vote: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
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
