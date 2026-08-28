package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

/**
 * Establishes that a sandbox image is on the daemon before a container is created from it.
 *
 * <p>The image is referenced only while a job runs, so a host that prunes unused images reclaims it
 * between jobs. Fetching it once at startup leaves the job's retry to fail identically, because
 * nothing re-pulls.
 */
@FunctionalInterface
public interface SandboxImageGuard {
    void ensurePresent(String image);
}
