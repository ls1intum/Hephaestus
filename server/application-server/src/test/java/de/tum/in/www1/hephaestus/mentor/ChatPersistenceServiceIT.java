package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import de.tum.in.www1.hephaestus.mentor.document.Document;
import de.tum.in.www1.hephaestus.mentor.document.DocumentKind;
import de.tum.in.www1.hephaestus.mentor.document.DocumentRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests ensuring ChatPersistenceService handles document creation and updates
 * via transient data-* stream parts, constructing content from text deltas and persisting versions.
 */
public class ChatPersistenceServiceIT extends BaseIntegrationTest {

    @Autowired
    private ChatPersistenceService chatPersistenceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private User mentorUser;

    @BeforeEach
    void setup() {
        mentorUser = userRepository.findByLogin("mentor").orElseThrow();
    }

    @Test
    @Transactional
    @WithMentorUser
    void documentCreationAndUpdateShouldPersistVersions() {
        // Arrange: create a thread and processor
        ChatThread thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setUser(mentorUser);

        // Persist thread and simulate a parent message (user prompt)
        ChatMessage parent = new ChatMessage();
        parent.setId(UUID.randomUUID());
        chatThreadRepository.save(thread);
        parent.setThread(thread);
        parent.setRole(ChatMessage.Role.USER);
        thread.addMessage(parent);
        chatMessageRepository.save(parent);

        StreamPartProcessor processor = chatPersistenceService.createProcessor(mentorUser, thread, parent);

        // Simulate start of assistant message
        processor.onStreamStart(new StreamStartPart().messageId(UUID.randomUUID().toString()));

        // Simulate document creation sequence like in the example
        // data-kind: text
        processor.onDataPart(new StreamDataPart().type("data-kind").data("text")._transient(true));
        // data-id: provide a deterministic id
        UUID docId = UUID.randomUUID();
        processor.onDataPart(new StreamDataPart().type("data-id").data(docId.toString())._transient(true));
        // data-title
        processor.onDataPart(new StreamDataPart().type("data-title").data("Short Poem")._transient(true));
        // data-clear
        processor.onDataPart(new StreamDataPart().type("data-clear").data(null)._transient(true));

        // text deltas building the content
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("# ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("Short ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("Poem\n\n")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("In ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("the ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("quiet ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("of ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("the ")._transient(true));
        processor.onDataPart(new StreamDataPart().type("data-textDelta").data("night,\n")._transient(true));

        // finish -> should create version 1
        processor.onDataPart(new StreamDataPart().type("data-finish").data(null)._transient(true));

        // Assert: one version exists with expected content prefix and metadata
        List<Document> versionsAfterCreate = documentRepository.findByIdAndUserOrderByCreatedAtDesc(docId, mentorUser);
        assertThat(versionsAfterCreate).hasSize(1);
        Document v1 = versionsAfterCreate.getFirst();
        assertThat(v1.getId()).isEqualTo(docId);
        assertThat(v1.getTitle()).isEqualTo("Short Poem");
        assertThat(v1.getKind()).isEqualTo(DocumentKind.TEXT);
        assertThat(v1.getContent()).startsWith("# Short Poem\n\nIn the quiet of the night,");

        Instant firstCreatedAt = v1.getCreatedAt();

        // Now simulate an update in a separate stream (separate assistant message)
        ChatMessage nextUserMessage = new ChatMessage();
        nextUserMessage.setId(UUID.randomUUID());
        nextUserMessage.setThread(thread);
        nextUserMessage.setRole(ChatMessage.Role.USER);
        chatMessageRepository.save(nextUserMessage);

        StreamPartProcessor updateProcessor = chatPersistenceService.createProcessor(
            mentorUser,
            thread,
            nextUserMessage
        );
        updateProcessor.onStreamStart(new StreamStartPart().messageId(UUID.randomUUID().toString()));

        // Emit only id (no title/kind) and clear + new content
        updateProcessor.onDataPart(new StreamDataPart().type("data-id").data(docId.toString())._transient(true));
        updateProcessor.onDataPart(new StreamDataPart().type("data-clear").data(null)._transient(true));
        updateProcessor.onDataPart(
            new StreamDataPart().type("data-textDelta").data("In the night's hush,\n")._transient(true)
        );
        updateProcessor.onDataPart(
            new StreamDataPart().type("data-textDelta").data("Stars whisper secrets.\n")._transient(true)
        );
        updateProcessor.onDataPart(new StreamDataPart().type("data-finish").data(null)._transient(true));

        // Assert: two versions exist, latest content reflects update
        List<Document> versionsAfterUpdate = documentRepository.findByIdAndUserOrderByCreatedAtDesc(docId, mentorUser);
        assertThat(versionsAfterUpdate).hasSize(2);
        Document latest = versionsAfterUpdate.get(0);
        Document older = versionsAfterUpdate.get(1);

        assertThat(latest.getCreatedAt()).isAfterOrEqualTo(firstCreatedAt);
        assertThat(latest.getTitle()).isEqualTo("Short Poem");
        assertThat(latest.getContent()).contains("In the night's hush,");
        assertThat(older.getContent()).startsWith("# Short Poem\n\nIn the quiet of the night,");
    }
}
