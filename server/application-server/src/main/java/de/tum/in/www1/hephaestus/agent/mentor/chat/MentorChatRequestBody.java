package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Wire shape of the body the webapp sends on {@code POST /mentor/chat}. Mirrors AI SDK's
 * {@code DefaultChatTransport} payload: an outer message envelope with an inner
 * {@code message.parts[]} array (we extract the text from the first {@code text} part).
 *
 * <p>Images are out of scope for v1 — any {@code message.parts} entry whose type is not
 * {@code text} is silently ignored. Add explicit support when the mentor accepts
 * attachments.
 *
 * @param id      the thread id (a brand new chat reuses the client-generated UUID).
 *                Service creates the row on the fly if it doesn't exist yet.
 * @param message the AI SDK UIMessage envelope; we read {@code message.parts[0].text}
 *                as the prompt. Other parts (images, files) are ignored in v1.
 */
@Schema(description = "Mentor chat turn request — AI SDK DefaultChatTransport shape.")
public record MentorChatRequestBody(UUID id, JsonNode message) {}
