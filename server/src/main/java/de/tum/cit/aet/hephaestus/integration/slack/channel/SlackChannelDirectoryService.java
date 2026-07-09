package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelDirectoryService {

    private final SlackMessageService slackMessageService;
    private final SlackMonitoredChannelRepository monitoredChannelRepository;

    public SlackChannelDirectoryService(
        SlackMessageService slackMessageService,
        SlackMonitoredChannelRepository monitoredChannelRepository
    ) {
        this.slackMessageService = slackMessageService;
        this.monitoredChannelRepository = monitoredChannelRepository;
    }

    @Transactional(readOnly = true)
    public java.util.List<SlackChannelCandidateDTO> listCandidates(long workspaceId) {
        Map<String, SlackMonitoredChannel> monitoredById = monitoredChannelRepository
            .findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
            .stream()
            .collect(Collectors.toMap(SlackMonitoredChannel::getSlackChannelId, Function.identity(), (a, b) -> a));

        return slackMessageService
            .listConversations(workspaceId)
            .stream()
            .map(channel -> {
                SlackMonitoredChannel monitored = monitoredById.get(channel.channelId());
                return new SlackChannelCandidateDTO(
                    channel.channelId(),
                    channel.channelName(),
                    channel.privateChannel(),
                    channel.member(),
                    channel.archived(),
                    monitored == null ? null : monitored.getConsentState()
                );
            })
            .sorted(Comparator.comparing(SlackChannelCandidateDTO::channelName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }
}
