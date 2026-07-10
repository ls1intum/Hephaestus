package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackChannelLifecycleService;

/**
 * Shared base for the Slack channel-lifecycle handlers ({@code channel_left}, {@code group_left},
 * {@code channel_archive}, {@code group_archive}, {@code channel_unarchive}, {@code channel_deleted},
 * {@code channel_rename}, {@code group_rename}). {@code EventTypeKey} indexes handlers one-per-event-type, so each
 * event gets its own tiny {@code @Component} subclass (see the sibling {@code Slack*MessageHandler} classes in this
 * package); this base only holds the shared {@link SlackChannelLifecycleService} wiring and constructor plumbing.
 * All decision logic lives in {@link SlackChannelLifecycleService} so it stays unit-testable without the NATS
 * envelope machinery.
 */
abstract class SlackChannelLifecycleMessageHandler extends AbstractSlackEnvelopeHandler {

    protected final SlackChannelLifecycleService lifecycleService;

    SlackChannelLifecycleMessageHandler(
        String eventType,
        SlackChannelLifecycleService lifecycleService,
        NatsMessageDeserializer deserializer
    ) {
        super(eventType, deserializer);
        this.lifecycleService = lifecycleService;
    }
}
