package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PracticePageFeedbackRouter {
    public boolean supportsPattern(Result about, Collection<Result> support) {
        return about.getDeveloperId() != null
                && about.getOutcome() == ResultOutcome.NOT_MET
                && support.stream()
                                .filter(row -> row.getWorkspaceId() == about.getWorkspaceId()
                                        && row.getPracticeId() == about.getPracticeId()
                                        && Objects.equals(row.getDeveloperId(), about.getDeveloperId())
                                        && row.getEndedAt() == null
                                        && row.getOutcome() == ResultOutcome.NOT_MET)
                                .map(Result::getItemId)
                                .distinct()
                                .count()
                        >= 2;
    }
}
