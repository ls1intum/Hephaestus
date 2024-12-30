package de.tum.in.www1.hephaestus.activity.badpracticedetector.guideline;

import de.tum.in.www1.hephaestus.activity.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PullRequestDescriptionBadPracticeGuideline implements PullRequestBadPracticeGuideline {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestDescriptionBadPracticeGuideline.class);

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    @Override
    public List<PullRequestBadPractice> detectBadPractices(PullRequest pullRequest) {
        logger.info("Checking pull request description for bad practices");

        List<PullRequestBadPractice> badPractices = new LinkedList<>();

        // Check pull request description guidelines
        checkEmptyDescription(pullRequest, badPractices);
        checkUncheckedCheckbox(pullRequest, badPractices);
        checkEmptyDescriptionSection(pullRequest, badPractices);

        return badPractices;
    }

    private void checkEmptyDescription(PullRequest pullRequest, List<PullRequestBadPractice> badPractices) {
        if (pullRequest.getBody().isEmpty()) {
            createAndAddBadPractice(pullRequest, PullRequestBadPracticeType.EMPTY_DESCRIPTION, badPractices);
        }
    }

    private void checkUncheckedCheckbox(PullRequest pullRequest, List<PullRequestBadPractice> badPractices) {
        if (pullRequest.getBody().contains("- [ ]")) {
            createAndAddBadPractice(pullRequest, PullRequestBadPracticeType.UNCHECKED_CHECKBOX, badPractices);
        }
    }

    private void checkEmptyDescriptionSection(PullRequest pullRequest, List<PullRequestBadPractice> badPractices) {
        String body = pullRequest.getBody();
        String[] lines = body.split("\n");
        Pattern sectionPattern = Pattern.compile("^#+\\s");

        for (int i = 0; i < lines.length; i++) {
            if (sectionPattern.matcher(lines[i].trim()).find()) {
                int sectionHeaderLength = lines[i].indexOf(' ') + 1;
                boolean isEmptySection = true;

                for (int j = i + 1; j < lines.length; j++) {
                    if (
                        sectionPattern.matcher(lines[j].trim()).find() &&
                        lines[j].indexOf(' ') + 1 <= sectionHeaderLength
                    ) {
                        break;
                    }
                    if (!lines[j].trim().isEmpty()) {
                        isEmptySection = false;
                        break;
                    }
                }

                if (isEmptySection) {
                    createAndAddBadPractice(
                        pullRequest,
                        PullRequestBadPracticeType.EMPTY_DESCRIPTION_SECTION,
                        badPractices
                    );
                }
            }
        }
    }

    private void createAndAddBadPractice(
        PullRequest pullRequest,
        PullRequestBadPracticeType type,
        List<PullRequestBadPractice> badPractices
    ) {
        logger.info("Found bad practice: {} for pull request: {}", type, pullRequest.getNumber());
        PullRequestBadPractice badPractice = new PullRequestBadPractice();
        badPractice.setType(type);
        badPractice.setPullrequest(pullRequest);
        badPractice.setResolved(false);
        badPractices.add(badPractice);
    }
}
