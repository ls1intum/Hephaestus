package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;

/** Product-owned catalog visibility and effective mode filtering for mentor context. */
public interface MentorPracticeQuery {
    List<MentorPractice> current(long workspaceId);

    record MentorPractice(String slug, PracticeDefinition definition) {}
}
