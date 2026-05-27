/**
 * Shared GitLab properties, token rotation client, rate-limit tracker, and webhook
 * client. Exposed via NamedInterface {@code common} so workspace provisioning can
 * configure default GitLab connections, rotate tokens, and create/delete webhooks.
 */
@org.springframework.modulith.NamedInterface("common")
package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common;
