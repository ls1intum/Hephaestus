package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import java.util.UUID;

/**
 * Published by {@code FeedbackLedgerRecorder} once per detection cycle that reached the ledger, so a listener can
 * route the cycle's observations for conversational delivery and prepare the PREPARED CONVERSATION units.
 *
 * <p><b>Fires for every cycle</b>, including comms-only cycles that post nothing in-context and cycles whose
 * in-context summary was a transient no-op - NOT only the delivered-in-context path (which early-returns).
 *
 * @param agentJobId  the agent job whose observations should be routed for conversational delivery
 * @param workspaceId the workspace scope
 */
public record PracticeDetectionDeliveredEvent(UUID agentJobId, Long workspaceId) {}
