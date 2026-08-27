package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import java.util.Map;
import java.util.Optional;

/**
 * Daemon-level Docker operations: connectivity check + image cache management. Split out from
 * {@link DockerContainerOperations} so the container-lifecycle interface stays under the ISP
 * method-count limit, and to make the rolling-deploy image-pull contract explicit.
 */
interface DockerImageOperations {
    /**
     * Pull an image into the local Docker daemon cache.
     *
     * <p>Used at executor startup against the configured agent image to defeat stale-{@code :latest}
     * races on rolling deploys — {@code createContainer} otherwise uses whatever the daemon has
     * cached and never re-pulls.
     *
     * @return {@code true} on successful pull, {@code false} on any failure (logged; not thrown)
     */
    boolean pullImage(String image);

    /**
     * Check whether an image exists in the local Docker daemon cache.
     * Used to implement {@link ImagePullPolicy#IF_NOT_PRESENT} logic without pulling.
     *
     * @return {@code true} if the image is present locally, {@code false} if absent or on error
     */
    boolean imageIsPresent(String image);

    /**
     * Read the OCI labels of a locally cached image.
     *
     * <p>Answers "may I run my runners in this?" without starting a container: the agent image
     * stamps the runtime contract it was built against, and an image the daemon cannot describe is
     * reported as absent rather than as unlabelled.
     *
     * @return the image's labels, or {@link Optional#empty()} if it is not cached locally
     *     or the daemon could not be reached (never empty labels vs. missing image conflated)
     */
    Optional<Map<String, String>> imageLabels(String image);

    /**
     * Ping the Docker daemon to verify connectivity.
     *
     * <p>Mirror of {@link DockerContainerOperations#ping()} — kept on both interfaces because the
     * image bootstrapper needs daemon-reachability before attempting a pull and should not have to
     * depend on the larger container interface.
     */
    boolean ping();
}
