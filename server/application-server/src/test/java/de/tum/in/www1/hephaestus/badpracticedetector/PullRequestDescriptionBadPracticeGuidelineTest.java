package de.tum.in.www1.hephaestus.badpracticedetector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.guideline.PullRequestBadPracticeGuideline;
import de.tum.in.www1.hephaestus.activity.badpracticedetector.guideline.PullRequestDescriptionBadPracticeGuideline;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PullRequestDescriptionBadPracticeGuidelineTest {

    PullRequestBadPracticeGuideline guideline = new PullRequestDescriptionBadPracticeGuideline();
    PullRequest pullRequest;
    Label readyLabel;

    String emptyDescription = "";
    String emptySection =
        """
        ## Section 1
        ### Section 2
        ### Section 3
        test
        """;
    String emptyCheckbox =
        """
        ### Section 1
        - [x] Test
        - [ ]
        """;

    @BeforeEach
    public void setUp() {
        pullRequest = new PullRequest();
        readyLabel = new Label();
        readyLabel.setName("ready-to-merge");
        pullRequest.setLabels(Set.of(readyLabel));
    }

    @Test
    void checkEmptyDescription() {
        pullRequest.setBody(emptyDescription);

        List<PullRequestBadPractice> badPractices = guideline.detectBadPractices(pullRequest);

        assertThat(badPractices, hasSize(1));
        assertThat(badPractices.get(0).getType(), is(PullRequestBadPracticeType.EMPTY_DESCRIPTION));
    }

    @Test
    void checkEmptySection() {
        pullRequest.setBody(emptySection);

        List<PullRequestBadPractice> badPractices = guideline.detectBadPractices(pullRequest);

        assertThat(badPractices, hasSize(1));
        assertThat(badPractices.get(0).getType(), is(PullRequestBadPracticeType.EMPTY_DESCRIPTION_SECTION));
    }

    @Test
    void checkEmptyCheckbox() {
        pullRequest.setBody(emptyCheckbox);

        List<PullRequestBadPractice> badPractices = guideline.detectBadPractices(pullRequest);

        assertThat(badPractices, hasSize(1));
        assertThat(badPractices.get(0).getType(), is(PullRequestBadPracticeType.UNCHECKED_CHECKBOX));
    }
}
