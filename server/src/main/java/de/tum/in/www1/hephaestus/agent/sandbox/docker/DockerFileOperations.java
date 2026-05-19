package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import java.io.InputStream;

/**
 * File transfer operations against the Docker daemon.
 *
 * <p>Uses {@code docker cp} (tar archive API) for injecting files into containers and extracting
 * output.
 */
interface DockerFileOperations {
    /**
     * Copy a tar archive into a container.
     *
     * @param containerId the target container
     * @param remotePath destination path inside the container
     * @param tarStream the tar archive input stream (caller closes)
     */
    void copyArchiveToContainer(String containerId, String remotePath, InputStream tarStream);

    /**
     * Copy a host directory into a container via {@code docker cp}.
     *
     * <p>Reads the directory from the Docker API client's filesystem, creates a tar archive,
     * and streams it to the Docker daemon. Works identically for local and remote daemons.
     *
     * @param containerId the target container (must be created, can be stopped)
     * @param hostPath absolute path on the client's filesystem
     * @param remotePath destination path inside the container
     */
    void copyHostDirectoryToContainer(String containerId, String hostPath, String remotePath);

    /**
     * Copy files from a container as a tar archive.
     *
     * @param containerId the source container
     * @param remotePath path inside the container to copy
     * @return tar archive input stream (caller must close)
     */
    InputStream copyArchiveFromContainer(String containerId, String remotePath);
}
