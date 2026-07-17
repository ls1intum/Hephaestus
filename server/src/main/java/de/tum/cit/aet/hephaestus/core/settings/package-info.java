/**
 * Instance settings — the singleton, instance-wide operator configuration (issue #1386).
 *
 * <p>Holds exactly one row ({@code instance_settings}, id = 1, seeded by Liquibase) so reads never
 * race a lazy bootstrap. First (and so far only) control: the <b>emergency silent mode</b> brake —
 * when engaged, Hephaestus stops writing outbound content (PR/MR + issue feedback comments, Slack
 * messages) instance-wide. It is a maintenance/incident control, not part of the per-workspace
 * rollout flow (#1357).
 *
 * <p>Cross-module consumers (agent delivery, Slack messaging) reach this package only through the
 * {@code core.settings.spi} named interface ({@code settings-spi}), mirroring {@code core.auth.spi}.
 */
package de.tum.cit.aet.hephaestus.core.settings;
