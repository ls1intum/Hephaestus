package de.tum.in.www1.hephaestus.gitprovider.label.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

@Component
public class GitHubLabelMessageHandler extends GitHubMessageHandler<GHEventPayload.Label> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelMessageHandler.class);

    private GitHubLabelMessageHandler() {
        super(GHEventPayload.Label.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.Label eventPayload) {
        logger.info("Received label event: {}", eventPayload);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.LABEL;
    }
}
