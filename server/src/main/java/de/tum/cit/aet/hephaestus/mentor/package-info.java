/**
 * Mentor module — durable REST + JPA for the Pi-powered chat mentor. Owns the parts that
 * survive swapping the underlying agent runtime: HTTP controllers, JPA entities, services.
 *
 * <h2>Split with {@code agent/mentor/}</h2>
 * Pi-runner infrastructure (translator, JSON-RPC client, SSE orchestration, wire chunks)
 * lives in {@link de.tum.cit.aet.hephaestus.agent.mentor}. The split mirrors
 * {@code practices/} ↔ {@code agent/practice/}.
 *
 * <h2>Key entry points</h2>
 * <ul>
 *   <li>{@link de.tum.cit.aet.hephaestus.mentor.ChatThreadController} — thread CRUD</li>
 *   <li>{@link de.tum.cit.aet.hephaestus.mentor.ChatMessageVoteController} — votes</li>
 *   <li>{@link de.tum.cit.aet.hephaestus.mentor.ChatThread} / {@link de.tum.cit.aet.hephaestus.mentor.ChatMessage}
 *       — entities backing {@code chat_thread}, {@code chat_message}</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Mentor")
package de.tum.cit.aet.hephaestus.mentor;
