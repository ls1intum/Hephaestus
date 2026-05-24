/**
 * Messaging family library.
 *
 * <p>Family-shared abstractions for messaging vendors (Slack, future Discord / Microsoft Teams).
 * Includes {@code MessagingFeedbackChannel}, sealed {@code MessagingComponent} (PlainText,
 * BlockKit, AdaptiveCard, DiscordComponentV2), {@code MessagingDomainEvent}, and value-record
 * {@code MessageRef} types.
 *
 * <p>Salesforce 2025-05 Slack API ToS prohibits persistence of message content. Therefore
 * {@code MessageRef} types are value records, NEVER {@code @Entity}. An ArchUnit rule enforces this.
 */
@org.springframework.modulith.NamedInterface({"api", "events"})
package de.tum.cit.aet.hephaestus.integration.messaging;
