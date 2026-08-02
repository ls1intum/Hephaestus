/**
 * Instance-wide operator settings: one row shared by every workspace, holding the emergency
 * silent-mode brake. A maintenance and incident control, not the per-workspace rollout flow.
 *
 * <p>Cross-module consumers reach this package only through the {@code core.settings.spi} named
 * interface, mirroring {@code core.auth.spi}.
 */
package de.tum.cit.aet.hephaestus.core.settings;
