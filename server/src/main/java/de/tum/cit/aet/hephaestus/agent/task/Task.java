package de.tum.cit.aet.hephaestus.agent.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Objects;

/**
 * Sealed root of all agent task variants serialised to {@code /workspace/task.json}. Mentor chat
 * is interactive (stdin JSON-RPC) and lives outside this hierarchy.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({ @JsonSubTypes.Type(value = Task.PracticeReview.class, name = Task.PracticeReview.KIND) })
public sealed interface Task {
    /**
     * Practice-review task: the Pi agent reads workspace context (PR diff, metadata, practice
     * catalog) and emits structured findings. PR-specific data lives in {@code context/target/}
     * files materialised by the content providers — this record only carries the prompt and
     * routing hints needed by the runner.
     */
    @JsonTypeName(Task.PracticeReview.KIND)
    record PracticeReview(String prompt, int pullRequestNumber, String repositoryFullName) implements Task {
        public static final String KIND = "practice_review";

        public PracticeReview {
            Objects.requireNonNull(prompt, "prompt");
            if (prompt.isBlank()) {
                throw new IllegalArgumentException("prompt must not be blank");
            }
            Objects.requireNonNull(repositoryFullName, "repositoryFullName");
            if (repositoryFullName.isBlank()) {
                throw new IllegalArgumentException("repositoryFullName must not be blank");
            }
            if (pullRequestNumber <= 0) {
                throw new IllegalArgumentException("pullRequestNumber must be positive, got " + pullRequestNumber);
            }
        }
    }
}
