package de.tum.in.www1.hephaestus.activity.badpracticedetector.guideline;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.List;

public interface PullRequestBadPracticeGuideline {
    List<PullRequestBadPractice> detectBadPractices(PullRequest pullRequest);
}
