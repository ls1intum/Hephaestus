/**
 * GitHub installation repository enumeration — exposed to the workspace provisioning
 * flow so a freshly-installed GitHub App can pull its accessible repo list before any
 * webhook fires.
 *
 * <p>Named interface: {@code installation}.
 */
@org.springframework.modulith.NamedInterface("installation")
package de.tum.cit.aet.hephaestus.integration.scm.github.installation;
