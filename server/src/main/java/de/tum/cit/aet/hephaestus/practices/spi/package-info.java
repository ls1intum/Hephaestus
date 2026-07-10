/**
 * Service Provider Interfaces for the {@code practices} module.
 *
 * <p><strong>Required (outbound) ports</strong> — {@code UserRoleChecker}, {@code AgentConfigChecker}:
 * implemented outside the module (in {@code notification}, {@code agent}) and called by the practices internals.
 *
 * <p><strong>Provided (inbound) ports</strong> — {@code ConversationFeedbackErasure}: implemented INSIDE the
 * module ({@code practices.adapter}) and called by a source module ({@code integration.slack}) to erase the
 * CONVERSATION_THREAD-derived observations/feedback for a set of threads. The dependency runs one way
 * ({@code integration.slack → practices::spi}), so no Spring Modulith cycle forms.
 */
@org.springframework.modulith.NamedInterface("spi")
package de.tum.cit.aet.hephaestus.practices.spi;
