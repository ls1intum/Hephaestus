package de.tum.in.www1.hephaestus.agent.mentor;

import java.util.Objects;
import java.util.UUID;

/**
 * Prior-turn Pi SDK session JSONL bytes injected at {@code .sessions/<threadId>.jsonl}
 * before the runner spawns. Pi's {@code switchSession} restores byte-identical state.
 */
public record SessionRestore(UUID threadId, byte[] bytes) {
    public SessionRestore {
        Objects.requireNonNull(threadId, "threadId");
        Objects.requireNonNull(bytes, "bytes");
    }
}
