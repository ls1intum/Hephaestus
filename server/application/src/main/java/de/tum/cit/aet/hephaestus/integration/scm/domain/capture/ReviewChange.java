package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** The paths and diff text that capture retained for the pinned change. */
interface ReviewChange {
    /** Both the old and new paths of a rename count. */
    Set<String> changedPaths();

    String text();

    static @Nullable ReviewChange of(Map<String, byte[]> staged, ObjectMapper mapper) {
        var bytes = staged.get("context/change.json");
        if (bytes == null) return null;
        try {
            var change = mapper.readTree(bytes);
            var paths = change.path("changed_paths");
            var diff = change.path("diff");
            if (!paths.isArray() || !diff.isString()) return null;
            var capturedPaths = new HashSet<String>();
            for (var path : paths) {
                if (!path.isString()) return null;
                capturedPaths.add(path.asString());
            }
            return new Captured(capturedPaths, diff.asString());
        } catch (JacksonException malformed) {
            return null;
        }
    }

    record Captured(Set<String> changedPaths, String text) implements ReviewChange {
        public Captured {
            changedPaths = Set.copyOf(changedPaths);
        }
    }
}
