package de.tum.cit.aet.hephaestus.agent.task;

import java.util.Objects;

/** Runtime instructions; domain work and its version come from the pinned assignment inputs. */
public record Task(String prompt) {
    public Task {
        Objects.requireNonNull(prompt, "prompt");
        if (prompt.isBlank()) throw new IllegalArgumentException("A task needs a prompt");
    }
}
