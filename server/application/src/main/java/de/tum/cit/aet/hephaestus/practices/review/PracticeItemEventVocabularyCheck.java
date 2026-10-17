package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

// Gate validation on the server role; shared coverage reads must not crash the webhook role.
@Component
@ConditionalOnServerRole
public class PracticeItemEventVocabularyCheck {

    private final PracticeItemEventCoverage coverage;

    public PracticeItemEventVocabularyCheck(PracticeItemEventCoverage coverage) {
        this.coverage = coverage;
    }

    @PostConstruct
    void verify() {
        coverage.validateAuthoringVocabulary();
    }
}
