package de.tum.cit.aet.hephaestus.integration.outline.documentation;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Source identity facts used for permission checks without loading document content. */
public record OutlineDocumentAccess(
        long workspaceId,
        long id,
        long connectionId,
        @Nullable String createdBySubject,
        @Nullable String updatedBySubject,
        @Nullable List<String> collaboratorSubjects,
        @Nullable Instant deletedAt) {
    public OutlineDocumentAccess {
        if (workspaceId <= 0 || id <= 0 || connectionId <= 0)
            throw new IllegalArgumentException("Document access requires its exact workspace and source ids");
        if (collaboratorSubjects != null) collaboratorSubjects = List.copyOf(collaboratorSubjects);
    }

    public List<String> subjects() {
        var subjects = new LinkedHashSet<String>();
        if (createdBySubject != null && !createdBySubject.isBlank()) subjects.add(createdBySubject);
        if (updatedBySubject != null && !updatedBySubject.isBlank()) subjects.add(updatedBySubject);
        if (collaboratorSubjects != null)
            collaboratorSubjects.stream().filter(subject -> !subject.isBlank()).forEach(subjects::add);
        return List.copyOf(subjects);
    }
}
