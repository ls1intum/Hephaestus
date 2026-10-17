package de.tum.cit.aet.hephaestus.integration.core.fabric;

import java.nio.file.Path;
import java.util.UUID;

/** Process ownership for temporary capture spools shared by writers and cleanup. */
public final class EvidenceSpool {
    public static final String PREFIX = "git-snapshot-";
    private static final String PROCESS_ID = UUID.randomUUID() + "-";

    private EvidenceSpool() {}

    public static String currentPrefix() {
        return PREFIX + PROCESS_ID;
    }

    public static boolean isCurrentProcess(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().startsWith(currentPrefix());
    }
}
