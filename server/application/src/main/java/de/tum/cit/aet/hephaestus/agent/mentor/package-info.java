/**
 * Pi-mentor infrastructure — replaceable layer between {@link de.tum.cit.aet.hephaestus.mentor}
 * (durable REST + JPA) and the Pi runner.
 *
 * <ul>
 *   <li>{@code chat/} — SSE orchestration ({@link MentorChatService}),
 *       JSON-RPC wrapper ({@link MentorRunnerClient}),
 *       per-thread mutex, persistence dispatch, AI-SDK {@code UIMessageChunk} wire types
 *       under {@code chat/wire/}, the Pi-event → UI-chunk translator alongside.</li>
 *   <li>{@link MentorPiAdapter} — sandbox-spec adapter,
 *       symmetric with {@code agent/practice/PracticePiAdapter}.</li>
 * </ul>
 *
 * <p>Turn cost is derived from the admission-frozen {@code LlmPriceSnapshot} — the single pricing
 * authority shared with the usage ledger in {@code agent.usage}; there is no separate pricing table.
 */
@NullMarked
package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatService;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorRunnerClient;
import org.jspecify.annotations.NullMarked;
