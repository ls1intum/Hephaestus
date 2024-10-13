package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHMilestoneState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;

@Component
public class GitHubMilestoneConverter extends BaseGitServiceEntityConverter<GHMilestone, Milestone> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneConverter.class);

    @Override
    public Milestone convert(@NonNull GHMilestone source) {
        return update(source, new Milestone());
    }

    @Override
    public Milestone update(@NonNull GHMilestone source, @NonNull Milestone milestone) {
        convertBaseFields(source, milestone);
        milestone.setNumber(source.getNumber());
        milestone.setState(convertState(source.getState()));
        milestone.setHtmlUrl(source.getHtmlUrl().toString());
        milestone.setTitle(source.getTitle());
        milestone.setDescription(source.getDescription());
        milestone.setDueOn(convertToOffsetDateTime(source.getDueOn()));
        try {
            milestone.setClosedAt(convertToOffsetDateTime(source.getClosedAt()));
        } catch (Exception e) {
            logger.error("Failed to convert closedAt field for source {}: {}", source.getId(), e.getMessage());
        }
        return milestone;
    }

    private Milestone.State convertState(GHMilestoneState state) {
        switch (state) {
            case OPEN:
                return Milestone.State.OPEN;
            case CLOSED:
                return Milestone.State.CLOSED;
            default:
                logger.error("Unknown milestone state: {}", state);
                return Milestone.State.CLOSED;
        }
    }
  }