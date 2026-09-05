package de.tum.cit.aet.hephaestus.testconfig;

import org.eclipse.jgit.lib.Repository;

/** Shared fixtures for tests that drive a real JGit repository. */
public final class GitTestFixtures {

    private GitTestFixtures() {}

    /**
     * JGit resolves {@code commit.gpgsign} and {@code tag.gpgsign} from the JVM's real
     * {@code ~/.gitconfig}, which it reads unconditionally (there is no JGit equivalent of
     * {@code GIT_CONFIG_GLOBAL} or {@code GIT_CONFIG_NOSYSTEM}). A developer following
     * `CONTRIBUTING.md` § Signed Commits has both set, so every fixture repository must turn
     * signing back off in its own local config, the only layer a test controls.
     */
    public static void disableSigning(Repository repository) {
        var config = repository.getConfig();
        config.setBoolean("commit", null, "gpgsign", false);
        config.setBoolean("tag", null, "gpgsign", false);
    }
}
