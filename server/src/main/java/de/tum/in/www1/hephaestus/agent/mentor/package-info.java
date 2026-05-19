/**
 * Pi-mentor infrastructure — replaceable layer between {@link de.tum.in.www1.hephaestus.mentor}
 * (durable REST + JPA) and the Pi runner.
 *
 * <ul>
 *   <li>{@code chat/} — SSE orchestration ({@link de.tum.in.www1.hephaestus.agent.mentor.chat.MentorChatService}),
 *       JSON-RPC wrapper ({@link de.tum.in.www1.hephaestus.agent.mentor.chat.MentorRunnerClient}),
 *       per-thread mutex, persistence dispatch, AI-SDK {@code UIMessageChunk} wire types
 *       under {@code chat/wire/}, the Pi-event → UI-chunk translator alongside.</li>
 *   <li>{@link de.tum.in.www1.hephaestus.agent.mentor.MentorPiAdapter} — sandbox-spec adapter,
 *       symmetric with {@code agent/practice/PracticePiAdapter}.</li>
 * </ul>
 *
 * <p>Model pricing was originally nested here; it now lives at {@link de.tum.in.www1.hephaestus.agent.pricing}
 * because the practice review pipeline will record cost on the same table.
 */
package de.tum.in.www1.hephaestus.agent.mentor;
