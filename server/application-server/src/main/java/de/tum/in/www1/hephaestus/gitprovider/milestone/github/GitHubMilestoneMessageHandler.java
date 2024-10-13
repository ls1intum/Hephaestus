package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

// TODO: Implement GHEventPayload.MilestoneEvent (not currently available in our GitHub API client)

// import org.kohsuke.github.GHEvent;
// import org.kohsuke.github.GHEventPayload;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.stereotype.Component;

// import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;

// @Component
// public class GitHubMilestoneMessageHandler extends GitHubMessageHandler<GHEventPayload.MilestoneEvent> {

//     private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneMessageHandler.class);

//     private GitHubMilestoneMessageHandler() {
//         super(GHEventPayload.MilestoneEvent.class);
//     }

//     @Override
//     protected void handleEvent(GHEventPayload.MilestoneEvent eventPayload) {
//         logger.info("Received label event: {}", eventPayload);
//     }

//     @Override
//     protected GHEvent getHandlerEvent() {
//         return GHEvent.MILESTONE;
//     }
// }