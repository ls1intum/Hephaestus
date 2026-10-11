package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import org.jspecify.annotations.Nullable;

/**
 * One call of a practice script: the model kind that the credential's path selected, and the practice
 * that the runner named in {@link JobTokenAuthenticationFilter#PRACTICE_SCRIPT_PRACTICE_HEADER}.
 *
 * @param tier the data handling tier of the model that serves the call, as {@link ProxyRouting#practiceScriptTier()}
 *     gives it
 * @param practiceSlug already matched against the practice slug pattern; the runner holds the only
 *     practice script credential, so the proxy does not check it against the review's practices
 */
public record PracticeScriptCall(ModelKind kind, @Nullable DataHandlingTier tier, String practiceSlug) {}
