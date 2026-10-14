package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;

public interface MentorPracticeQuery {
    List<MentorPractice> current(long workspaceId);

    record MentorPractice(String slug, PracticeDefinition definition) {}
}
