/**
 * Mentor module — durable REST + JPA for the Pi-powered chat mentor. Owns the parts that
 * survive swapping the underlying agent runtime: HTTP controllers, JPA entities, services.
 *
 * <h2>Split with {@code agent/mentor/}</h2>
 * Pi-runner infrastructure (translator, JSON-RPC client, SSE orchestration, wire chunks)
 * lives in {@link de.tum.in.www1.hephaestus.agent.mentor}. The split mirrors
 * {@code practices/} ↔ {@code agent/practice/}.
 *
 * <h2>Key entry points</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.mentor.ChatThreadController} — thread CRUD</li>
 *   <li>{@link de.tum.in.www1.hephaestus.mentor.ChatMessageVoteController} — votes</li>
 *   <li>{@link de.tum.in.www1.hephaestus.mentor.ChatThread} / {@link de.tum.in.www1.hephaestus.mentor.ChatMessage}
 *       — entities backing {@code chat_thread}, {@code chat_message}</li>
 * </ul>
 */
package de.tum.in.www1.hephaestus.mentor;
