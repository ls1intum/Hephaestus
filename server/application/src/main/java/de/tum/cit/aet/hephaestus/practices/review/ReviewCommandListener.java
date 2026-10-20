package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.events.BotCommandReceivedEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/** Turns {@code /hephaestus review} on a pull or merge request into the review request the work page makes. */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewCommandListener {
    private static final Logger log = LoggerFactory.getLogger(ReviewCommandListener.class);
    private static final String REVIEW = "/hephaestus review";

    private final ReviewRequestService requests;

    // Not joined: a refused request rolls back its own transaction, and the refusal is the handled outcome.
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(BotCommandReceivedEvent command) {
        String text = command.noteBody().strip().toLowerCase(Locale.ROOT);
        if (!text.equals(REVIEW) && !text.startsWith(REVIEW + " ")) {
            log.debug(
                    "Ignored an unknown bot command: workspaceId={}, itemId={}",
                    command.workspaceId(),
                    command.itemId());
            return;
        }
        var key = UUID.nameUUIDFromBytes((command.kind() + ":" + command.providerId() + ":" + command.commentId())
                .getBytes(StandardCharsets.UTF_8));
        try {
            var outcome = requests.requestByComment(
                    command.workspaceId(),
                    command.itemId(),
                    new PersonIdentity(command.providerId(), Long.toString(command.authorNativeId()), null),
                    key);
            log.info(
                    "Review command: status={}, reason={}, reviewId={}, workspaceId={}, itemId={}",
                    outcome.status(),
                    outcome.reason(),
                    outcome.reviewId(),
                    command.workspaceId(),
                    command.itemId());
        } catch (AccessForbiddenException
                | EntityNotFoundException
                | ItemCaptureException
                | ReviewRequestRejected refused) {
            log.warn(
                    "Review command refused: workspaceId={}, itemId={}, reasonType={}",
                    command.workspaceId(),
                    command.itemId(),
                    refused.getClass().getName());
        }
    }
}
