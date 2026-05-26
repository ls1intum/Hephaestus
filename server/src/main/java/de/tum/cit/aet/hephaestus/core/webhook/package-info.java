/**
 * Shared webhook configuration properties bound to {@code hephaestus.webhook.*}, consumed by
 * both sides of the contract:
 * <ul>
 *   <li>{@code workspace.GitLabWebhookService} — auto-registers webhooks with GitLab using
 *       {@code externalUrl} and {@code secret}; rotates PATs per {@code tokenRotation}.</li>
 *   <li>{@code integration.webhook.*} — verifies incoming bodies against {@code secret} and
 *       publishes to JetStream per {@code publish}, {@code stream}, {@code shutdown}.</li>
 * </ul>
 *
 * <p>Lives in {@code core} so both feature modules can depend on it without forming a cycle.
 */
@org.springframework.modulith.NamedInterface("webhook")
package de.tum.cit.aet.hephaestus.core.webhook;
