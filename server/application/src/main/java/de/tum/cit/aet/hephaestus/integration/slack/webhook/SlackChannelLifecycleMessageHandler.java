package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.handler.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackChannelLifecycleService;

abstract class SlackChannelLifecycleMessageHandler extends AbstractSlackEnvelopeHandler {

    protected final SlackChannelLifecycleService lifecycleService;

    SlackChannelLifecycleMessageHandler(
            String eventType, SlackChannelLifecycleService lifecycleService, NatsMessageDeserializer deserializer) {
        super(eventType, deserializer);
        this.lifecycleService = lifecycleService;
    }
}
