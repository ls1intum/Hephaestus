package de.tum.in.www1.hephaestus.gitprovider.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects real GitHub webhook payloads into test methods.
 * Uses actual webhook JSON files to catch API changes early.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface GitHubPayload {
    /**
     * The file name pattern (e.g., "label.created", "issue.opened", "milestone"). This will be
     * resolved to {value}.json in test resources.
     */
    String value();
}
